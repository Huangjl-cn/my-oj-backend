package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.mapper.NoteMapper;
import com.hjl.oj.model.dto.note.NoteAddRequest;
import com.hjl.oj.model.dto.note.NoteQueryRequest;
import com.hjl.oj.model.dto.note.NoteUpdateRequest;
import com.hjl.oj.model.entity.Note;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.NoteSummaryVO;
import com.hjl.oj.model.vo.NoteVO;
import com.hjl.oj.service.NoteService;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.utils.NoteSnippets;
import com.hjl.oj.utils.QuestionStats;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记：只有作者自己能看能改。保存用版本号做乐观锁：两处同时改，后保存的会收到冲突，不会悄悄覆盖。
 */
@Service
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements NoteService {

    static final String UNTITLED = "未命名笔记";

    static final int MAX_NOTES_PER_USER = 2000;

    static final int MAX_PAGE_SIZE = 50;

    static final int SNIPPET_LENGTH = 90;

    /**
     * 列表只取正文开头这么多字做摘要，不把整篇正文读出来
     */
    private static final int SNIPPET_SOURCE_LENGTH = 400;

    @Resource
    private QuestionService questionService;

    @Override
    public NoteVO addNote(NoteAddRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long count = this.count(new QueryWrapper<Note>().eq("userId", loginUser.getId()));
        ThrowUtils.throwIf(count >= MAX_NOTES_PER_USER, ErrorCode.OPERATION_ERROR,
                "最多 " + MAX_NOTES_PER_USER + " 篇笔记");
        Question question = null;
        if (request.getQuestionId() != null) {
            question = questionService.getById(request.getQuestionId());
            ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        Note note = new Note();
        note.setUserId(loginUser.getId());
        note.setQuestionId(question == null ? null : question.getId());
        String title = StringUtils.trimToNull(request.getTitle());
        if (title == null) {
            title = question == null ? UNTITLED : StringUtils.abbreviate(question.getTitle() + " 笔记", MAX_TITLE_LENGTH);
        }
        note.setTitle(checkTitle(title));
        note.setContent(checkContent(request.getContent()));
        note.setVersion(0);
        ThrowUtils.throwIf(!this.save(note), ErrorCode.OPERATION_ERROR);
        return getNote(note.getId(), loginUser);
    }

    @Override
    public NoteVO updateNote(NoteUpdateRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getId() == null || request.getVersion() == null,
                ErrorCode.PARAMS_ERROR);
        String title = StringUtils.defaultIfBlank(StringUtils.trimToNull(request.getTitle()), UNTITLED);
        boolean updated = this.update(new UpdateWrapper<Note>()
                .eq("id", request.getId())
                .eq("userId", loginUser.getId())
                .eq("version", request.getVersion())
                .set("title", checkTitle(title))
                .set("content", checkContent(request.getContent()))
                .setSql("version = version + 1"));
        if (!updated) {
            // 区分「不存在（或不是自己的）」和「在别处改过了」
            getOwned(request.getId(), loginUser);
            throw new BusinessException(ErrorCode.CONFLICT_ERROR);
        }
        return getNote(request.getId(), loginUser);
    }

    @Override
    public boolean deleteNote(long noteId, User loginUser) {
        getOwned(noteId, loginUser);
        return this.removeById(noteId);
    }

    @Override
    public NoteVO getNote(long noteId, User loginUser) {
        Note note = getOwned(noteId, loginUser);
        Map<Long, Question> questions = loadQuestions(
                note.getQuestionId() == null ? List.of() : List.of(note.getQuestionId()));
        NoteVO vo = new NoteVO();
        vo.setId(note.getId());
        vo.setTitle(note.getTitle());
        vo.setContent(StringUtils.defaultString(note.getContent()));
        vo.setVersion(note.getVersion());
        vo.setCreateTime(note.getCreateTime());
        vo.setUpdateTime(note.getUpdateTime());
        vo.setQuestionId(note.getQuestionId());
        Question question = questionOf(questions, note.getQuestionId());
        if (question != null) {
            vo.setQuestionTitle(question.getTitle());
            vo.setQuestionTags(QuestionStats.parseTags(question.getTags()));
        }
        return vo;
    }

    @Override
    public Page<NoteSummaryVO> listMyNotes(NoteQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getCurrent() <= 0 || request.getPageSize() <= 0
                || request.getPageSize() > MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR);
        QueryWrapper<Note> wrapper = new QueryWrapper<Note>()
                .select("id", "questionId", "title", "updateTime",
                        "LEFT(content, " + SNIPPET_SOURCE_LENGTH + ") AS content")
                .eq("userId", loginUser.getId())
                .eq(request.getQuestionId() != null, "questionId", request.getQuestionId())
                .orderByDesc("updateTime")
                .orderByDesc("id");
        String keyword = StringUtils.trimToNull(request.getKeyword());
        if (keyword != null) {
            String pattern = "%" + escapeLike(StringUtils.left(keyword, 50)) + "%";
            wrapper.and(inner -> inner.apply("title LIKE {0}", pattern).or().apply("content LIKE {0}", pattern));
        }
        Page<Note> page = this.page(new Page<>(request.getCurrent(), request.getPageSize()), wrapper);
        Map<Long, Question> questions = loadQuestions(page.getRecords().stream()
                .map(Note::getQuestionId).filter(Objects::nonNull).distinct().toList());
        Page<NoteSummaryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(note -> {
            NoteSummaryVO vo = new NoteSummaryVO();
            vo.setId(note.getId());
            vo.setTitle(note.getTitle());
            vo.setSnippet(NoteSnippets.plain(note.getContent(), SNIPPET_LENGTH));
            vo.setQuestionId(note.getQuestionId());
            vo.setUpdateTime(note.getUpdateTime());
            Question question = questionOf(questions, note.getQuestionId());
            if (question != null) {
                vo.setQuestionTitle(question.getTitle());
                vo.setQuestionTags(QuestionStats.parseTags(question.getTags()));
            }
            return vo;
        }).toList());
        return result;
    }

    /**
     * 自己的笔记；别人的或已删除的当作不存在
     */
    private Note getOwned(long noteId, User loginUser) {
        Note note = this.getById(noteId);
        if (note == null || !Objects.equals(note.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        return note;
    }

    /**
     * 自由笔记没有题目；Map.of() 不能用 null 查，所以单独判断
     */
    private static Question questionOf(Map<Long, Question> questions, Long questionId) {
        return questionId == null ? null : questions.get(questionId);
    }

    private Map<Long, Question> loadQuestions(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionService.list(new QueryWrapper<Question>().select("id", "title", "tags").in("id", ids))
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity(), (first, second) -> first));
    }

    private static String checkTitle(String title) {
        ThrowUtils.throwIf(title.length() > MAX_TITLE_LENGTH, ErrorCode.PARAMS_ERROR,
                "标题最多 " + MAX_TITLE_LENGTH + " 个字");
        return title;
    }

    private static String checkContent(String content) {
        String value = StringUtils.defaultString(content);
        ThrowUtils.throwIf(value.length() > MAX_CONTENT_LENGTH, ErrorCode.PARAMS_ERROR,
                "笔记最多 " + MAX_CONTENT_LENGTH / 10_000 + " 万字");
        return value;
    }

    /**
     * LIKE 里的 % 和 _ 按普通字符找（MySQL 默认的转义符是反斜杠）
     */
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
