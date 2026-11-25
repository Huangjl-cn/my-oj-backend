package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionSession;
import com.hjl.oj.model.vo.CompanionConversationVO;
import com.hjl.oj.service.CompanionMessageService;
import com.hjl.oj.service.CompanionSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionConversationServiceImplTest {

    private static final long USER_ID = 1L;

    private static final long QUESTION_ID = 2L;

    private final CompanionSessionService sessionService = mock(CompanionSessionService.class);

    private final CompanionMessageService messageService = mock(CompanionMessageService.class);

    private CompanionConversationServiceImpl service;

    private CompanionSession session;

    @BeforeEach
    void setUp() {
        service = spy(new CompanionConversationServiceImpl(sessionService, messageService));
        session = new CompanionSession();
        session.setId(9L);
        session.setUserId(USER_ID);
        session.setQuestionId(QUESTION_ID);
        session.setActiveConversationId(3L);
        doReturn(true).when(service).removeById(any(Long.class));
        // 保存时模拟数据库分配 id
        doAnswer(invocation -> {
            invocation.<CompanionConversation>getArgument(0).setId(4L);
            return true;
        }).when(service).save(any(CompanionConversation.class));
    }

    @Test
    void startNewReusesActiveConversationThatHasNoMessages() {
        CompanionConversation active = conversation(3L);
        doReturn(active).when(service).getOne(any(Wrapper.class));
        when(messageService.hasMessages(3L)).thenReturn(false);

        assertSame(active, service.startNew(session));
        verify(service, never()).save(any(CompanionConversation.class));
        verify(sessionService, never()).updateActiveConversation(anyLong(), anyLong());
    }

    @Test
    void startNewCreatesAndActivatesWhenActiveHasMessages() {
        doReturn(conversation(3L), conversation(4L)).when(service).getOne(any(Wrapper.class));
        when(messageService.hasMessages(3L)).thenReturn(true);

        assertEquals(4L, service.startNew(session).getId());
        verify(sessionService).updateActiveConversation(9L, 4L);
        assertEquals(4L, session.getActiveConversationId());
    }

    @Test
    void concurrentFirstEntryKeepsTheConversationThatWonTheClaim() {
        session.setActiveConversationId(null);
        doReturn(conversation(4L), conversation(8L)).when(service).getOne(any(Wrapper.class));
        when(sessionService.claimActiveConversation(9L, 4L)).thenReturn(false);
        CompanionSession latest = new CompanionSession();
        latest.setActiveConversationId(8L);
        when(sessionService.getById(9L)).thenReturn(latest);

        assertEquals(8L, service.getActive(session).getId());
        verify(service).removeById(4L);
        assertEquals(8L, session.getActiveConversationId());
    }

    @Test
    void activateRemovesPreviousConversationOnlyWhenItHasNoMessages() {
        when(messageService.hasMessages(3L)).thenReturn(false);
        service.activate(session, conversation(5L));
        verify(sessionService).updateActiveConversation(9L, 5L);
        verify(service).removeById(3L);

        when(messageService.hasMessages(5L)).thenReturn(true);
        service.activate(session, conversation(6L));
        verify(service, never()).removeById(5L);
    }

    @Test
    void listShowsActiveAndConversationsWithMessages() {
        when(messageService.countByConversation(USER_ID, QUESTION_ID)).thenReturn(Map.of(5L, 4L));
        doReturn(List.of(conversation(3L), conversation(5L), conversation(6L))).when(service).list(any(Wrapper.class));

        List<CompanionConversationVO> list = service.listForQuestion(session);

        assertEquals(List.of(3L, 5L), list.stream().map(CompanionConversationVO::getId).toList());
        assertEquals(List.of(true, false), list.stream().map(CompanionConversationVO::getActive).toList());
        assertEquals(List.of(0, 4), list.stream().map(CompanionConversationVO::getMessageCount).toList());
    }

    @Test
    void titleIsTheFlattenedStartOfTheFirstQuestion() {
        assertEquals("为什么 第三个用例 超时了", CompanionConversationServiceImpl.toTitle("  为什么\n第三个用例\t超时了 "));
        assertEquals("一二三四五六七八九十一二三四五六七八九十…",
                CompanionConversationServiceImpl.toTitle("一二三四五六七八九十一二三四五六七八九十多出来的"));
    }

    private static CompanionConversation conversation(long id) {
        CompanionConversation conversation = new CompanionConversation();
        conversation.setId(id);
        conversation.setUserId(USER_ID);
        conversation.setQuestionId(QUESTION_ID);
        return conversation;
    }
}
