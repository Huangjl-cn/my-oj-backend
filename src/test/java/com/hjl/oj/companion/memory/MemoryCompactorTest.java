package com.hjl.oj.companion.memory;

import com.hjl.oj.companion.assistant.CompanionCompactionAssistant;
import com.hjl.oj.companion.prompt.CompanionPrompts;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryCompactorTest {

    private static final SessionMemoryId MEMORY_ID = new SessionMemoryId(1L, 2L);

    private MysqlChatMemoryStore store;

    private CompanionPrompts prompts;

    private MemoryCompactor compactor;

    private final List<String> compactionInputs = new ArrayList<>();

    private final CompanionCompactionAssistant assistant = (systemPrompt, message) -> {
        compactionInputs.add(message);
        return Result.<String>builder().content("## 解题进展\n用户在尝试哈希表").build();
    };

    @BeforeEach
    void setUp() {
        store = mock(MysqlChatMemoryStore.class);
        prompts = mock(CompanionPrompts.class);
        when(prompts.compactionMessage(any(), anyString())).thenAnswer(invocation ->
                "PREVIOUS=" + invocation.getArgument(0) + "\nTRANSCRIPT=" + invocation.getArgument(1));
        compactor = new MemoryCompactor(store, prompts);
    }

    @Test
    void keepsRecentRoundsAndToolPairsIntact() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder().id("t1").name("listMySubmissions").arguments("{}").build();
        List<ChatMessage> messages = List.of(
                SystemMessage.from("系统"),
                user("第一轮问题"), AiMessage.from("第一轮回答"),
                user("第二轮问题"), AiMessage.from(List.of(toolRequest)),
                ToolExecutionResultMessage.from(toolRequest, "提交记录"), AiMessage.from("第二轮回答"),
                user("第三轮问题"), AiMessage.from("第三轮回答"));
        when(store.getMessages(MEMORY_ID)).thenReturn(messages);

        Optional<MemoryCompactor.CompactionResult> result =
                compactor.compact(MEMORY_ID, assistant, "系统", 2, List.of("第一轮问题", "第二轮问题"));

        assertTrue(result.isPresent());
        assertEquals(1, result.get().compactedRounds());
        assertEquals(2, result.get().keptRounds());
        ArgumentCaptor<List<ChatMessage>> saved = captureSaved();
        List<ChatMessage> compacted = saved.getValue();
        assertTrue(MemoryCompactor.isNote(compacted.get(0)));
        assertEquals(MemoryCompactor.NOTE_ACK, ((AiMessage) compacted.get(1)).text());
        // 第二轮（含工具调用与结果）原样保留
        assertEquals(2 + 6, compacted.size());
        assertInstanceOf(ToolExecutionResultMessage.class, compacted.get(4));
        assertTrue(compactionInputs.get(0).contains("第一轮问题"));
        assertTrue(!compactionInputs.get(0).contains("第三轮问题"));
    }

    @Test
    void noteKeepsUserQuestionsVerbatim() {
        when(store.getMessages(MEMORY_ID)).thenReturn(List.of(user("问题一"), AiMessage.from("答一"),
                user("问题二"), AiMessage.from("答二")));

        String note = compactor.compact(MEMORY_ID, assistant, "系统", 1, List.of("问题一", "问题二"))
                .orElseThrow().note();

        assertTrue(note.contains("## 解题进展"));
        assertTrue(note.contains("## 用户问过的问题（原文）\n- 问题一\n- 问题二"));
    }

    @Test
    void mergesPreviousNoteOnSecondCompaction() {
        when(store.getMessages(MEMORY_ID)).thenReturn(List.of(
                UserMessage.from(MemoryCompactor.NOTE_PREFIX + "旧笔记内容"), AiMessage.from(MemoryCompactor.NOTE_ACK),
                user("新问题"), AiMessage.from("新回答"),
                user("最新问题"), AiMessage.from("最新回答")));

        compactor.compact(MEMORY_ID, assistant, "系统", 1, List.of());

        assertTrue(compactionInputs.get(0).contains("PREVIOUS=旧笔记内容"));
        assertTrue(compactionInputs.get(0).contains("新问题"));
    }

    @Test
    void doesNothingWithoutHistory() {
        when(store.getMessages(MEMORY_ID)).thenReturn(List.of(SystemMessage.from("系统")));

        assertTrue(compactor.compact(MEMORY_ID, assistant, "系统", 4, List.of()).isEmpty());
        verify(store, never()).updateMessages(eq(MEMORY_ID), any());
    }

    @Test
    void splitsVeryLongTranscriptIntoChunks() {
        assertEquals(3, MemoryCompactor.chunks("x".repeat(MemoryCompactor.CHUNK_CHARS * 2 + 1)).size());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ChatMessage>> captureSaved() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).updateMessages(eq(MEMORY_ID), captor.capture());
        return captor;
    }

    private static UserMessage user(String said) {
        return UserMessage.from("【场景】用户在聊天\n\n【用户当前代码】\n```\n1| code\n```\n\n【用户说】\n" + said);
    }
}
