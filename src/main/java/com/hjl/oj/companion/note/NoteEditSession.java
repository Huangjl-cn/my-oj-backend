package com.hjl.oj.companion.note;

import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.NoteService;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一次请求内鸭鸭对笔记的提议：新建、修改都只记在这里，不写进笔记。用户预览后点「应用」才写入。
 *
 * <p>同一篇笔记的多处修改合成一条提议，在工作副本上依次应用；返回给模型的都是可读的结果说明，失败时模型可以据此调整后重试。
 */
public class NoteEditSession {

    static final int MAX_NOTES_PER_TURN = 3;

    static final int MAX_CHANGES_PER_TURN = 10;

    static final int MAX_REASON_LENGTH = 200;

    private final User user;

    private final Long questionId;

    private final String questionTitle;

    private final int level;

    private final String userCode;

    private final NoteService noteService;

    /**
     * 按提出的先后排；修改的 key 是笔记 id，新建的 key 是负数序号
     */
    private final Map<Long, NoteProposal> proposals = new LinkedHashMap<>();

    private int changeCount;

    private long createSeq;

    /**
     * @param level    当前生效的提示档位，用来约束写进笔记的代码
     * @param userCode 用户编辑器里的代码：照抄进笔记的不算鸭鸭新写的
     */
    public NoteEditSession(User user, Long questionId, String questionTitle, int level, String userCode,
                           NoteService noteService) {
        this.user = user;
        this.questionId = questionId;
        this.questionTitle = questionTitle;
        this.level = level;
        this.userCode = StringUtils.defaultString(userCode);
        this.noteService = noteService;
    }

    public synchronized String create(String title, String content, boolean linkToQuestion, String reason) {
        Optional<String> limit = checkTurnLimit(true);
        if (limit.isPresent()) {
            return limit.get();
        }
        String body = NoteChanges.normalize(content).strip();
        if (body.isEmpty()) {
            return "提议失败：笔记内容不能为空";
        }
        String cleanTitle = StringUtils.trimToEmpty(title);
        if (cleanTitle.isEmpty()) {
            cleanTitle = linkToQuestion && questionTitle != null ? questionTitle + " 笔记" : "未命名笔记";
        }
        Optional<String> invalid = checkSize(cleanTitle, body).or(() -> NoteContentPolicy.check(body, level, userCode));
        if (invalid.isPresent()) {
            return "提议被拒绝：" + invalid.get();
        }
        NoteProposal proposal = new NoteProposal();
        proposal.setKind(NoteProposal.KIND_CREATE);
        proposal.setTitle(cleanTitle);
        if (linkToQuestion) {
            proposal.setQuestionId(questionId);
            proposal.setQuestionTitle(questionTitle);
        }
        proposal.setContent(body + "\n");
        proposal.setReason(reason(reason));
        proposals.put(-(++createSeq), proposal);
        changeCount++;
        return "已记录新建笔记「" + cleanTitle + "」的提议，用户预览后决定是否应用";
    }

    public synchronized String replace(String noteId, String oldText, String newText, String reason) {
        return change(noteId, new NoteChange(NoteChange.KIND_REPLACE, oldText, StringUtils.defaultString(newText),
                reason(reason)), null);
    }

    public synchronized String append(String noteId, String text, String reason) {
        return change(noteId, new NoteChange(NoteChange.KIND_APPEND, null, text, reason(reason)), null);
    }

    public synchronized String rewrite(String noteId, String newTitle, String newContent, String reason) {
        return change(noteId, new NoteChange(NoteChange.KIND_REWRITE, null, newContent, reason(reason)),
                StringUtils.trimToNull(newTitle));
    }

    public synchronized List<NoteProposal> proposals() {
        return List.copyOf(proposals.values());
    }

    private String change(String noteId, NoteChange change, String newTitle) {
        Long id = parseId(noteId);
        if (id == null) {
            return "笔记 id 格式不对，应该是一串数字，来自 listNotesOnThisQuestion、searchMyNotes 或 readNote 的结果";
        }
        NoteProposal proposal = proposals.get(id);
        if (proposal == null) {
            Optional<String> limit = checkTurnLimit(true);
            if (limit.isPresent()) {
                return limit.get();
            }
            NoteVO note;
            try {
                note = noteService.getNote(id, user);
            } catch (BusinessException e) {
                return "没有找到这篇笔记（只能改用户自己的笔记）";
            }
            proposal = new NoteProposal();
            proposal.setKind(NoteProposal.KIND_UPDATE);
            proposal.setNoteId(note.getId());
            proposal.setBaseVersion(note.getVersion());
            proposal.setTitle(note.getTitle());
            proposal.setPreviousTitle(note.getTitle());
            proposal.setQuestionId(note.getQuestionId());
            proposal.setQuestionTitle(note.getQuestionTitle());
            proposal.setContent(NoteChanges.normalize(note.getContent()));
        } else {
            Optional<String> limit = checkTurnLimit(false);
            if (limit.isPresent()) {
                return limit.get();
            }
        }

        String before = proposal.getContent();
        NoteChanges.Outcome outcome = NoteChanges.apply(before, change);
        if (!outcome.ok()) {
            return "修改失败：" + outcome.failure();
        }
        String title = newTitle == null ? proposal.getTitle() : newTitle;
        // 整篇改写时整篇都算新写的，但原文里已有的行照抄不算
        String added = NoteChange.KIND_REWRITE.equals(change.getKind()) ? outcome.content() : change.getNewText();
        Optional<String> invalid = checkSize(title, outcome.content())
                .or(() -> NoteContentPolicy.check(added, level, userCode, before));
        if (invalid.isPresent()) {
            return "修改被拒绝：" + invalid.get();
        }

        proposal.setTitle(title);
        proposal.setContent(outcome.content());
        proposal.getChanges().add(change);
        proposal.setReason(joinReasons(proposal.getChanges()));
        proposals.putIfAbsent(proposal.getNoteId(), proposal);
        changeCount++;
        return "已记录对笔记「" + title + "」的修改，用户预览后决定是否应用";
    }

    /**
     * @param newNote 这次会多出一篇笔记的提议
     */
    private Optional<String> checkTurnLimit(boolean newNote) {
        if (changeCount >= MAX_CHANGES_PER_TURN) {
            return Optional.of("提议失败：这一轮对笔记的修改已经达到 " + MAX_CHANGES_PER_TURN + " 处上限");
        }
        if (newNote && proposals.size() >= MAX_NOTES_PER_TURN) {
            return Optional.of("提议失败：这一轮最多涉及 " + MAX_NOTES_PER_TURN + " 篇笔记");
        }
        return Optional.empty();
    }

    private static Optional<String> checkSize(String title, String content) {
        if (title.length() > NoteService.MAX_TITLE_LENGTH) {
            return Optional.of("标题最多 " + NoteService.MAX_TITLE_LENGTH + " 个字");
        }
        if (content.length() > NoteService.MAX_CONTENT_LENGTH) {
            return Optional.of("笔记最多 " + NoteService.MAX_CONTENT_LENGTH + " 个字");
        }
        return Optional.empty();
    }

    private static String joinReasons(List<NoteChange> changes) {
        List<String> reasons = new ArrayList<>();
        for (NoteChange change : changes) {
            if (StringUtils.isNotBlank(change.getReason()) && !reasons.contains(change.getReason())) {
                reasons.add(change.getReason());
            }
        }
        return StringUtils.abbreviate(String.join("；", reasons), MAX_REASON_LENGTH);
    }

    private static String reason(String reason) {
        return StringUtils.abbreviate(StringUtils.trimToEmpty(reason), MAX_REASON_LENGTH);
    }

    private static Long parseId(String noteId) {
        try {
            return noteId == null ? null : Long.parseLong(noteId.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
