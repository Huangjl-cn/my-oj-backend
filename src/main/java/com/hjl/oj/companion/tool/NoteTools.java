package com.hjl.oj.companion.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.companion.note.NoteEditSession;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.note.NoteQueryRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.NoteSummaryVO;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.NoteService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 给模型读写用户笔记的工具。用户和题目在创建时固定，只能看到、改到用户自己的笔记；
 * 新建和修改都只记成提议（NoteEditSession），用户预览后点「应用」才写进笔记。
 */
public class NoteTools {

    static final int LIST_LIMIT = 10;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final User user;

    private final long questionId;

    private final NoteService noteService;

    private final NoteEditSession editSession;

    private final int maxResultChars;

    public NoteTools(User user, long questionId, NoteService noteService, NoteEditSession editSession,
                     int maxResultChars) {
        this.user = user;
        this.questionId = questionId;
        this.noteService = noteService;
        this.editSession = editSession;
        this.maxResultChars = maxResultChars;
    }

    @Tool("列出用户挂在本题上的笔记（id、标题、开头摘要、更新时间），最近修改的在前")
    public String listNotesOnThisQuestion() {
        NoteQueryRequest request = query();
        request.setQuestionId(questionId);
        Page<NoteSummaryVO> page = noteService.listMyNotes(request, user);
        if (page.getRecords().isEmpty()) {
            return "用户在本题还没有笔记";
        }
        return render(page, false);
    }

    @Tool("在用户的全部笔记里按关键词找（标题和正文），最多返回 " + LIST_LIMIT + " 篇；关键词留空时返回最近修改的几篇")
    public String searchMyNotes(@P("关键词，比如算法名、题目名；可以留空") String keyword) {
        NoteQueryRequest request = query();
        request.setKeyword(StringUtils.trimToNull(keyword));
        Page<NoteSummaryVO> page = noteService.listMyNotes(request, user);
        if (page.getRecords().isEmpty()) {
            return request.getKeyword() == null ? "用户还没有笔记" : "没有找到包含「" + request.getKeyword() + "」的笔记";
        }
        return render(page, true);
    }

    @Tool("读取用户某篇笔记的完整正文（Markdown）。改笔记前先读，原文要从这里原样复制")
    public String readNote(@P("笔记 id，来自 listNotesOnThisQuestion 或 searchMyNotes 的结果") String noteId) {
        Long id = parseId(noteId);
        if (id == null) {
            return "笔记 id 格式不对，应该是一串数字";
        }
        NoteVO note;
        try {
            note = noteService.getNote(id, user);
        } catch (BusinessException e) {
            return "没有找到这篇笔记（只能看用户自己的笔记）";
        }
        String text = "笔记 id " + note.getId() + "，标题「" + note.getTitle() + "」，" + where(note.getQuestionId(),
                note.getQuestionTitle()) + "，" + formatTime(note.getUpdateTime()) + " 更新\n正文：\n"
                + (StringUtils.isBlank(note.getContent()) ? "（空的）" : note.getContent());
        return text.length() <= maxResultChars ? text
                : text.substring(0, maxResultChars) + "\n……（正文过长，后面没有显示；修改时只能引用上面看到的部分）";
    }

    @Tool("提议新建一篇笔记。不会直接保存：用户先预览，点「应用」才建")
    public String createNote(@P("标题，简短；留空时用「题目名 笔记」") String title,
                             @P("正文，Markdown") String content,
                             @P("是否挂在本题上；和本题无关的笔记传 false") boolean linkToThisQuestion,
                             @P("一句话说明这篇笔记记了什么，会展示给用户") String reason) {
        return editSession.create(title, content, linkToThisQuestion, reason);
    }

    @Tool("提议修改用户的一篇笔记：把正文里的一段原文替换成新内容。原文必须从 readNote 读到的正文里原样复制，并且只出现一次；"
            + "不会直接保存，用户先预览再决定是否应用")
    public String editNote(@P("笔记 id") String noteId,
                           @P("要替换的原文，原样复制且在正文中唯一") String oldText,
                           @P("替换后的新内容") String newText,
                           @P("为什么这样改，一句话，会展示给用户") String reason) {
        return editSession.replace(noteId, oldText, newText, reason);
    }

    @Tool("提议在用户的一篇笔记末尾追加内容（和前文空一行）。不会直接保存，用户先预览再决定是否应用")
    public String appendToNote(@P("笔记 id") String noteId,
                               @P("要追加的内容，Markdown") String text,
                               @P("一句话说明追加了什么，会展示给用户") String reason) {
        return editSession.append(noteId, text, reason);
    }

    @Tool("提议整篇改写用户的一篇笔记（比如重新整理结构）。只在用户要求整理、重写时使用，局部改动用 editNote；"
            + "不会直接保存，用户先预览再决定是否应用")
    public String rewriteNote(@P("笔记 id") String noteId,
                              @P("新标题；不改标题就留空") String newTitle,
                              @P("新的完整正文，Markdown") String newContent,
                              @P("一句话说明改了什么，会展示给用户") String reason) {
        return editSession.rewrite(noteId, newTitle, newContent, reason);
    }

    private static NoteQueryRequest query() {
        NoteQueryRequest request = new NoteQueryRequest();
        request.setCurrent(1);
        request.setPageSize(LIST_LIMIT);
        return request;
    }

    private String render(Page<NoteSummaryVO> page, boolean withQuestion) {
        StringBuilder text = new StringBuilder();
        if (page.getTotal() > page.getRecords().size()) {
            text.append("共 ").append(page.getTotal()).append(" 篇，下面是最近修改的 ").append(page.getRecords().size())
                    .append(" 篇\n");
        }
        for (NoteSummaryVO note : page.getRecords()) {
            text.append("- 笔记 id ").append(note.getId()).append("，「").append(note.getTitle()).append("」");
            if (withQuestion) {
                text.append("，").append(where(note.getQuestionId(), note.getQuestionTitle()));
            }
            text.append("，").append(formatTime(note.getUpdateTime())).append(" 更新");
            if (StringUtils.isNotBlank(note.getSnippet())) {
                text.append("\n  开头：").append(note.getSnippet());
            }
            text.append("\n");
        }
        String result = text.toString();
        return result.length() <= maxResultChars ? result : result.substring(0, maxResultChars) + "\n……（已截断）";
    }

    private String where(Long noteQuestionId, String noteQuestionTitle) {
        if (noteQuestionId == null) {
            return "自由笔记";
        }
        String title = noteQuestionTitle == null ? "（题目已删除）" : "「" + noteQuestionTitle + "」";
        return (noteQuestionId == questionId ? "挂在本题" : "挂在题目") + title + "上";
    }

    private static String formatTime(Date time) {
        return time == null ? "时间未知" : TIME_FORMAT.format(time.toInstant());
    }

    private static Long parseId(String noteId) {
        try {
            return noteId == null ? null : Long.parseLong(noteId.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
