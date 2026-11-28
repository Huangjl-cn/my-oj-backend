package com.hjl.oj.companion.trace;

import com.hjl.oj.companion.tool.NoteTools;
import com.hjl.oj.model.entity.CompanionTraceStep;
import com.hjl.oj.model.entity.CompanionTraceTurn;
import com.hjl.oj.service.CompanionTraceService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceRecorderTest {

    private final CompanionTraceService store = mock(CompanionTraceService.class);

    private final TraceRecorder recorder = new TraceRecorder(store, 5L, 7L);

    private final ChatRequest request = ChatRequest.builder()
            .messages(SystemMessage.from("系统提示词"), UserMessage.from("上一轮"), AiMessage.from("上一轮的回答"),
                    UserMessage.from("【场景】聊天\n\n【用户说】\n这一轮"))
            .toolSpecifications(ToolSpecifications.toolSpecificationsFrom(NoteTools.class))
            .build();

    @Test
    void theFirstModelCallDescribesTheTurnOnce() {
        recorder.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, new HashMap<>()));
        recorder.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, new HashMap<>()));

        verify(store, times(1)).describeTurn(eq(5L), eq(7L), eq("系统提示词"), eq("【场景】聊天\n\n【用户说】\n这一轮"),
                argThat(tools -> tools.stream().anyMatch(tool -> tool.getName().equals("createNote"))));
    }

    @Test
    void eachModelCallIsSavedWithTokensAndToolCalls() {
        Map<Object, Object> attributes = new HashMap<>();
        recorder.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, attributes));
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("1").name("readNote").arguments("{\"noteId\":\"9\"}").build())))
                .modelName("deepseek-flash")
                .tokenUsage(OpenAiTokenUsage.builder()
                        .inputTokenCount(100)
                        .outputTokenCount(5)
                        .totalTokenCount(105)
                        .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(80).build())
                        .build())
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();

        recorder.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes));

        ArgumentCaptor<CompanionTraceStep> step = ArgumentCaptor.forClass(CompanionTraceStep.class);
        verify(store).saveStep(step.capture());
        assertEquals(CompanionTraceStep.KIND_MODEL, step.getValue().getKind());
        assertEquals("deepseek-flash", step.getValue().getName());
        assertEquals(100, step.getValue().getInputTokens());
        assertEquals(80, step.getValue().getCachedTokens());
        assertEquals("TOOL_EXECUTION", step.getValue().getFinishReason());
        assertTrue(step.getValue().getToolCalls().contains("readNote"));
        assertTrue(step.getValue().getDurationMs() >= 0);
    }

    @Test
    void failedModelCallsAndToolsAreSavedToo() {
        recorder.onError(new ChatModelErrorContext(new RuntimeException("余额不足"), request, ModelProvider.OTHER,
                new HashMap<>()));
        ToolExecution execution = mock(ToolExecution.class);
        when(execution.request()).thenReturn(ToolExecutionRequest.builder().id("1").name("readNote")
                .arguments("{}").build());
        when(execution.result()).thenReturn("没有找到这篇笔记");
        when(execution.startTime()).thenReturn(LocalDateTime.now().minusNanos(30_000_000));
        when(execution.duration()).thenReturn(Duration.ofMillis(30));
        recorder.recordTool(execution);

        ArgumentCaptor<CompanionTraceStep> steps = ArgumentCaptor.forClass(CompanionTraceStep.class);
        verify(store, times(2)).saveStep(steps.capture());
        assertEquals("余额不足", steps.getAllValues().get(0).getError());
        CompanionTraceStep tool = steps.getAllValues().get(1);
        assertEquals(CompanionTraceStep.KIND_TOOL, tool.getKind());
        assertEquals("readNote", tool.getName());
        assertEquals("没有找到这篇笔记", tool.getOutput());
        assertNull(tool.getError());
        assertTrue(tool.getDurationMs() >= 20, "耗时取自工具执行的开始和结束时间");
    }

    @Test
    void aTurnEndsOnceAndRecordingFailuresAreSwallowed() {
        doThrow(new RuntimeException("数据库挂了")).when(store).saveStep(any());

        recorder.onError(new ChatModelErrorContext(new RuntimeException("x"), request, ModelProvider.OTHER,
                new HashMap<>()));
        recorder.finish(30L);
        recorder.fail(new RuntimeException("晚到的错误"));

        verify(store, times(1)).finishTurn(5L, CompanionTraceTurn.STATUS_OK, 30L, null);
        verify(store, never()).finishTurn(anyLong(), eq(CompanionTraceTurn.STATUS_ERROR), any(), anyString());
    }

    @Test
    void theDisabledRecorderDoesNothing() {
        TraceRecorder disabled = TraceRecorder.disabled();
        disabled.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, new HashMap<>()));
        disabled.finish(1L);
        verify(store, never()).describeTurn(anyLong(), anyLong(), any(), any(), anyList());
    }
}
