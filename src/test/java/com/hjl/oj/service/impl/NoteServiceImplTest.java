package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.note.NoteAddRequest;
import com.hjl.oj.model.dto.note.NoteUpdateRequest;
import com.hjl.oj.model.entity.Note;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteServiceImplTest {

    private final QuestionService questionService = mock(QuestionService.class);

    private NoteServiceImpl service;

    private final User me = user(1L);

    @BeforeEach
    void setUp() {
        service = spy(new NoteServiceImpl());
        ReflectionTestUtils.setField(service, "questionService", questionService);
        doReturn(0L).when(service).count(any(Wrapper.class));
        doReturn(List.of()).when(service).list(any(Wrapper.class));
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void notesOnAQuestionAreNamedAfterIt() {
        when(questionService.getById(5L)).thenReturn(question(5L, "两数之和"));
        ArgumentCaptor<Note> saved = captureSave();
        NoteAddRequest request = new NoteAddRequest();
        request.setQuestionId(5L);

        service.addNote(request, me);

        assertEquals("两数之和 笔记", saved.getValue().getTitle());
        assertEquals(5L, saved.getValue().getQuestionId());
        assertEquals("", saved.getValue().getContent());
        assertEquals(0, saved.getValue().getVersion());
    }

    @Test
    void freeNotesWithoutATitleAreUntitled() {
        ArgumentCaptor<Note> saved = captureSave();
        NoteAddRequest request = new NoteAddRequest();
        request.setTitle("   ");
        request.setContent("随手记");

        service.addNote(request, me);

        assertEquals("未命名笔记", saved.getValue().getTitle());
        assertNull(saved.getValue().getQuestionId());
        assertEquals("随手记", saved.getValue().getContent());
    }

    @Test
    void notesOnMissingQuestionsAreRejected() {
        when(questionService.getById(5L)).thenReturn(null);
        NoteAddRequest request = new NoteAddRequest();
        request.setQuestionId(5L);

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addNote(request, me)).getCode());
        verify(service, never()).save(any(Note.class));
    }

    @Test
    void sizeLimitsAreChecked() {
        NoteAddRequest tooLong = new NoteAddRequest();
        tooLong.setContent("字".repeat(100_001));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addNote(tooLong, me)).getCode());

        NoteAddRequest longTitle = new NoteAddRequest();
        longTitle.setTitle("题".repeat(81));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addNote(longTitle, me)).getCode());

        doReturn(2000L).when(service).count(any(Wrapper.class));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.addNote(new NoteAddRequest(), me)).getCode());
    }

    @Test
    void savingWithAnOldVersionIsAConflict() {
        doReturn(false).when(service).update(any(Wrapper.class));
        doReturn(note(9L, 1L)).when(service).getById(9L);

        assertEquals(ErrorCode.CONFLICT_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.updateNote(update(9L, 3), me)).getCode());
    }

    @Test
    void otherPeoplesNotesLookLikeTheyDoNotExist() {
        doReturn(false).when(service).update(any(Wrapper.class));
        doReturn(note(9L, 2L)).when(service).getById(9L);

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.updateNote(update(9L, 3), me)).getCode());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.getNote(9L, me)).getCode());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.deleteNote(9L, me)).getCode());
    }

    @Test
    void savingChecksTheVersionAndBumpsIt() {
        doReturn(true).when(service).update(any(Wrapper.class));
        doReturn(note(9L, 1L)).when(service).getById(9L);
        NoteUpdateRequest request = update(9L, 3);
        request.setTitle("  ");

        service.updateNote(request, me);

        ArgumentCaptor<UpdateWrapper<Note>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(service).update(captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("version = version + 1"), sqlSet);
        // 条件里的参数要等生成 SQL 时才登记，先生成一次
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("version") && where.contains("userId"), where);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(3));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("未命名笔记"));
    }

    @Test
    void likeWildcardsInKeywordsAreSearchedLiterally() {
        assertEquals("100\\%\\_a\\\\b", NoteServiceImpl.escapeLike("100%_a\\b"));
    }

    private ArgumentCaptor<Note> captureSave() {
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        // 保存时模拟数据库分配 id；之后按 id 读回来的就是刚保存的那篇
        doAnswer(invocation -> {
            invocation.<Note>getArgument(0).setId(9L);
            return true;
        }).when(service).save(captor.capture());
        doAnswer(invocation -> captor.getValue()).when(service).getById(9L);
        return captor;
    }

    private static NoteUpdateRequest update(long id, int version) {
        NoteUpdateRequest request = new NoteUpdateRequest();
        request.setId(id);
        request.setVersion(version);
        request.setTitle("思路");
        request.setContent("正文");
        return request;
    }

    private static Note note(long id, long userId) {
        Note note = new Note();
        note.setId(id);
        note.setUserId(userId);
        note.setTitle("笔记");
        note.setVersion(4);
        return note;
    }

    private static Question question(long id, String title) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(title);
        question.setTags("[\"easy\"]");
        return question;
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
