package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.trace.TraceTool;
import com.hjl.oj.mapper.CompanionTraceStepMapper;
import com.hjl.oj.model.entity.CompanionConversation;
import com.hjl.oj.model.entity.CompanionMessage;
import com.hjl.oj.model.entity.CompanionTraceStep;
import com.hjl.oj.model.entity.CompanionTraceTurn;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.CompanionTraceTurnVO;
import com.hjl.oj.model.vo.CompanionTraceVO;
import com.hjl.oj.service.CompanionConversationService;
import com.hjl.oj.service.CompanionMessageService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionTraceServiceImplTest {

    private static final long CONVERSATION_ID = 50L;

    private static final long T0 = 1_800_000_000_000L;

    private final CompanionTraceStepMapper stepMapper = mock(CompanionTraceStepMapper.class);

    private final CompanionConversationService conversations = mock(CompanionConversationService.class);

    private final CompanionMessageService messages = mock(CompanionMessageService.class);

    private final MysqlChatMemoryStore memory = mock(MysqlChatMemoryStore.class);

    private CompanionTraceServiceImpl service;

    private final User me = new User();

    @BeforeEach
    void setUp() {
        me.setId(1L);
        service = spy(new CompanionTraceServiceImpl(stepMapper, conversations, messages, memory));
        CompanionConversation conversation = new CompanionConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(1L);
        conversation.setTitle("两数之和怎么做");
        when(conversations.requireOwned(1L, CONVERSATION_ID)).thenReturn(conversation);
    }

    @Test
    void promptsAndToolsCarryOverToLaterTurns() {
        CompanionTraceTurn chat = turn(11L, "chat", 0, "S1", "[{\"name\":\"readNote\",\"parameters\":[]}]");
        chat.setUserText("你好");
        chat.setUserPrompt("【场景】用户在陪练面板里和你聊天。\n\n【用户说】\n你好");
        CompanionTraceTurn hint = turn(12L, "hint", 5000, null, "[]");
        CompanionTraceTurn fallback = turn(13L, "reaction", 9000, null, null);
        CompanionTraceTurn later = turn(14L, "chat", 20000, "S2", null);
        // 查询按 id 倒序取最近的，服务里再翻回来
        doReturn(new ArrayList<>(List.of(later, fallback, hint, chat))).when(service).list(any(Wrapper.class));
        // 工具这一步先写进库，但开始得晚：按开始时间排在模型调用后面
        when(stepMapper.selectList(any())).thenReturn(List.of(
                step(101L, 11L, "tool", 300), step(102L, 11L, "model", 10),
                step(103L, 12L, "model", 5010), step(104L, 14L, "model", 20010)));

        CompanionTraceVO vo = service.getTrace(me, CONVERSATION_ID);

        assertEquals("两数之和怎么做", vo.getTitle());
        assertTrue(vo.isRecordedFromStart());
        assertEquals(List.of("S1", "S2"), vo.getSystemPrompts());
        assertEquals(2, vo.getToolSets().size());
        List<CompanionTraceTurnVO> turns = vo.getTurns();
        assertEquals(List.of(11L, 12L, 13L, 14L), turns.stream().map(CompanionTraceTurnVO::getId).toList());
        assertEquals(Arrays.asList(0, 0, null, 1),
                turns.stream().map(CompanionTraceTurnVO::getSystemPromptIndex).toList());
        assertEquals(Arrays.asList(0, 1, null, 1), turns.stream().map(CompanionTraceTurnVO::getToolSetIndex).toList());
        assertEquals("你好", turns.get(0).getUserText());
        assertEquals(List.of("场景"), turns.get(0).getContext().stream().map(section -> section.getTitle()).toList());
        assertEquals(List.of("model", "tool"), turns.get(0).getSteps().stream().map(step -> step.getKind()).toList());
        assertEquals(1200, turns.get(0).getDurationMs());
    }

    @Test
    void recordingThatStartedMidConversationIsMarked() {
        doReturn(new ArrayList<>(List.of(turn(11L, "chat", 0, "S", "[]")))).when(service).list(any(Wrapper.class));
        when(stepMapper.selectList(any())).thenReturn(List.of());
        CompanionMessage first = new CompanionMessage();
        first.setCreateTime(new Date(T0 - 60_000));
        when(messages.getOne(any(Wrapper.class))).thenReturn(first);

        assertFalse(service.getTrace(me, CONVERSATION_ID).isRecordedFromStart());
    }

    @Test
    void conversationsWithoutRecordsAreRebuiltFromMemory() {
        doReturn(new ArrayList<>()).when(service).list(any(Wrapper.class));
        when(messages.hasMessages(CONVERSATION_ID)).thenReturn(true);
        ToolExecutionRequest list = ToolExecutionRequest.builder().id("1").name("listNotesOnThisQuestion")
                .arguments("{}").build();
        when(memory.getMessages(any())).thenReturn(List.of(
                SystemMessage.from("系统提示词"),
                UserMessage.from("【场景】用户在陪练面板里和你聊天。\n\n【用户说】\n看看我的笔记"),
                AiMessage.from(List.of(list)),
                ToolExecutionResultMessage.from(list, "用户在本题还没有笔记"),
                AiMessage.from("你还没有笔记哦"),
                UserMessage.from("【场景】用户点了「给点提示」，当时的档位是第 1 级。"),
                AiMessage.from("想想边界")));

        CompanionTraceVO vo = service.getTrace(me, CONVERSATION_ID);

        assertTrue(vo.isFromMemory());
        assertFalse(vo.isRecordedFromStart());
        assertEquals(List.of("系统提示词"), vo.getSystemPrompts());
        assertEquals(List.of("chat", "hint"), vo.getTurns().stream().map(CompanionTraceTurnVO::getType).toList());
        CompanionTraceTurnVO chat = vo.getTurns().get(0);
        assertEquals("看看我的笔记", chat.getUserText());
        assertEquals(List.of("model", "tool", "model"), chat.getSteps().stream().map(step -> step.getKind()).toList());
        assertEquals("listNotesOnThisQuestion", chat.getSteps().get(0).getToolCalls().get(0).getName());
        assertNull(chat.getSteps().get(0).getDurationMs());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unchangedPromptsAndToolsAreNotStoredAgain() {
        CompanionTraceTurn previous = new CompanionTraceTurn();
        previous.setSystemPrompt("S");
        previous.setTools("[]");
        doReturn(previous).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).update(any(Wrapper.class));

        service.describeTurn(12L, CONVERSATION_ID, "S", "用户消息", List.of());
        service.describeTurn(13L, CONVERSATION_ID, "S2", "用户消息",
                List.of(new TraceTool("readNote", "读一篇笔记", List.of())));

        ArgumentCaptor<UpdateWrapper<CompanionTraceTurn>> updates = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(service, times(2)).update(updates.capture());
        String same = updates.getAllValues().get(0).getSqlSet();
        assertTrue(same.contains("userPrompt") && !same.contains("systemPrompt") && !same.contains("tools"), same);
        String changed = updates.getAllValues().get(1).getSqlSet();
        assertTrue(changed.contains("systemPrompt") && changed.contains("tools"), changed);
    }

    private static CompanionTraceTurn turn(long id, String type, long offset, String systemPrompt, String tools) {
        CompanionTraceTurn turn = new CompanionTraceTurn();
        turn.setId(id);
        turn.setConversationId(CONVERSATION_ID);
        turn.setType(type);
        turn.setStatus(CompanionTraceTurn.STATUS_OK);
        turn.setSystemPrompt(systemPrompt);
        turn.setTools(tools);
        turn.setStartTime(new Date(T0 + offset));
        turn.setEndTime(new Date(T0 + offset + 1200));
        return turn;
    }

    private static CompanionTraceStep step(long id, long turnId, String kind, long offset) {
        CompanionTraceStep step = new CompanionTraceStep();
        step.setId(id);
        step.setTurnId(turnId);
        step.setKind(kind);
        step.setStartTime(new Date(T0 + offset));
        step.setDurationMs(100);
        return step;
    }
}
