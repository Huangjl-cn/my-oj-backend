package com.hjl.oj.companion.trace;

import com.hjl.oj.model.entity.CompanionTraceStep;
import com.hjl.oj.model.entity.CompanionTraceTurn;
import com.hjl.oj.service.CompanionTraceService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一轮的轨迹记录器：作为模型监听器收下每次模型调用，工具执行完由流式回答交给它，结束时标记完成或出错。
 *
 * <p>记录只是附带的：任何一步写库失败都只记日志，不影响鸭鸭回答。
 */
@Slf4j
public class TraceRecorder implements ChatModelListener {

    /**
     * 存进库的正文、思考、工具结果最多这么多字
     */
    static final int MAX_TEXT_CHARS = 60_000;

    static final int MAX_ERROR_CHARS = 500;

    private static final String START_MILLIS = "trace.startMillis";

    private static final TraceRecorder DISABLED = new TraceRecorder(null, 0, 0);

    private final CompanionTraceService store;

    private final long turnId;

    private final long conversationId;

    private final AtomicBoolean described = new AtomicBoolean();

    private final AtomicBoolean finished = new AtomicBoolean();

    public TraceRecorder(CompanionTraceService store, long turnId, long conversationId) {
        this.store = store;
        this.turnId = turnId;
        this.conversationId = conversationId;
    }

    /**
     * 什么也不记的记录器（开始一轮就写库失败时用）
     */
    public static TraceRecorder disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return store != null;
    }

    public long turnId() {
        return turnId;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START_MILLIS, System.currentTimeMillis());
        if (!enabled() || !described.compareAndSet(false, true)) {
            return;
        }
        // 一轮里第一次调用模型时，记下系统提示词、这一轮的用户消息和提供的工具
        ChatRequest request = context.chatRequest();
        safely("记录这一轮的输入", () -> store.describeTurn(turnId, conversationId, systemText(request.messages()),
                lastUserText(request.messages()), TracePrompts.describe(request.toolSpecifications())));
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        if (!enabled()) {
            return;
        }
        safely("记录模型调用", () -> {
            ChatResponse response = context.chatResponse();
            CompanionTraceStep step = modelStep(context.attributes().get(START_MILLIS));
            step.setName(response.modelName());
            AiMessage message = response.aiMessage();
            if (message != null) {
                step.setOutput(clip(message.text()));
                step.setThinking(clip(message.thinking()));
                if (message.hasToolExecutionRequests()) {
                    List<TraceToolCall> calls = message.toolExecutionRequests().stream()
                            .map(TraceRecorder::toolCall)
                            .toList();
                    step.setToolCalls(TraceJson.write(calls));
                }
            }
            TokenUsage usage = response.tokenUsage();
            if (usage != null) {
                step.setInputTokens(usage.inputTokenCount());
                step.setOutputTokens(usage.outputTokenCount());
                if (usage instanceof OpenAiTokenUsage openAiUsage && openAiUsage.inputTokensDetails() != null) {
                    step.setCachedTokens(openAiUsage.inputTokensDetails().cachedTokens());
                }
            }
            step.setFinishReason(response.finishReason() == null ? null : response.finishReason().name());
            store.saveStep(step);
        });
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        if (!enabled()) {
            return;
        }
        safely("记录出错的模型调用", () -> {
            CompanionTraceStep step = modelStep(context.attributes().get(START_MILLIS));
            step.setError(errorText(context.error()));
            store.saveStep(step);
        });
    }

    /**
     * 一个工具执行完了（流式回答的 onToolExecuted）
     */
    public void recordTool(ToolExecution execution) {
        if (!enabled() || execution == null) {
            return;
        }
        safely("记录工具调用", () -> {
            CompanionTraceStep step = new CompanionTraceStep();
            step.setTurnId(turnId);
            step.setConversationId(conversationId);
            step.setKind(CompanionTraceStep.KIND_TOOL);
            ToolExecutionRequest request = execution.request();
            step.setName(request == null ? null : StringUtils.left(request.name(), 64));
            step.setInput(request == null ? null : clip(request.arguments()));
            step.setOutput(clip(execution.result()));
            if (execution.hasFailed()) {
                step.setError(StringUtils.abbreviate(StringUtils.defaultIfBlank(execution.result(), "工具执行失败"),
                        MAX_ERROR_CHARS));
            }
            long now = System.currentTimeMillis();
            long duration = execution.duration() == null ? 0 : execution.duration().toMillis();
            step.setStartTime(execution.startTime() == null ? new Date(now - duration)
                    : Date.from(execution.startTime().atZone(ZoneId.systemDefault()).toInstant()));
            step.setDurationMs((int) Math.max(0, duration));
            store.saveStep(step);
        });
    }

    public void finish(Long messageId) {
        end(CompanionTraceTurn.STATUS_OK, messageId, null);
    }

    /**
     * 模型没调用成功，用了兜底话术（提交后反应）
     */
    public void fallback(Long messageId, Throwable cause) {
        end(CompanionTraceTurn.STATUS_FALLBACK, messageId, cause == null ? null : errorText(cause));
    }

    public void fail(Throwable cause) {
        end(CompanionTraceTurn.STATUS_ERROR, null, errorText(cause));
    }

    /**
     * 这一轮其实什么也没做，删掉
     */
    public void discard() {
        if (enabled() && finished.compareAndSet(false, true)) {
            safely("删掉空的一轮", () -> store.discardTurn(turnId));
        }
    }

    private void end(String status, Long messageId, String error) {
        if (enabled() && finished.compareAndSet(false, true)) {
            safely("结束这一轮", () -> store.finishTurn(turnId, status, messageId, error));
        }
    }

    private CompanionTraceStep modelStep(Object startMillis) {
        long now = System.currentTimeMillis();
        long start = startMillis instanceof Long value ? value : now;
        CompanionTraceStep step = new CompanionTraceStep();
        step.setTurnId(turnId);
        step.setConversationId(conversationId);
        step.setKind(CompanionTraceStep.KIND_MODEL);
        step.setStartTime(new Date(start));
        step.setDurationMs((int) Math.max(0, now - start));
        return step;
    }

    private static TraceToolCall toolCall(ToolExecutionRequest request) {
        return new TraceToolCall(request.name(), request.arguments());
    }

    static String systemText(List<ChatMessage> messages) {
        return messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElse(null);
    }

    static String lastUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage user && user.hasSingleText()) {
                return user.singleText();
            }
        }
        return null;
    }

    static String errorText(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName());
        return StringUtils.abbreviate(message, MAX_ERROR_CHARS);
    }

    static String clip(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS) + "\n……（过长，后面没有记录）";
    }

    private static void safely(String action, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("鸭鸭轨迹{}失败，不影响回答：{}", action, e.toString());
        }
    }
}
