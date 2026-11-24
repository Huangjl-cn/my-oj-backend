package com.hjl.oj.ai.listener;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

/**
 * 记录每次模型调用的用途、token 用量（含缓存命中）和耗时。只记数字，不记消息内容和 Key。
 */
@Slf4j
public class UsageLoggingListener implements ChatModelListener {

    private static final String START_NANOS = "usage.startNanos";

    private final String purpose;

    public UsageLoggingListener(String purpose) {
        this.purpose = purpose;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(START_NANOS, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        TokenUsage usage = responseContext.chatResponse().tokenUsage();
        Integer cachedTokens = null;
        if (usage instanceof OpenAiTokenUsage openAiUsage && openAiUsage.inputTokensDetails() != null) {
            cachedTokens = openAiUsage.inputTokensDetails().cachedTokens();
        }
        log.info("AI 调用完成 purpose={}, model={}, inputTokens={}, cachedTokens={}, outputTokens={}, costMs={}",
                purpose,
                responseContext.chatResponse().modelName(),
                usage == null ? null : usage.inputTokenCount(),
                cachedTokens,
                usage == null ? null : usage.outputTokenCount(),
                elapsedMillis(responseContext.attributes().get(START_NANOS)));
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("AI 调用失败 purpose={}, costMs={}, error={}",
                purpose, elapsedMillis(errorContext.attributes().get(START_NANOS)), errorContext.error().toString());
    }

    private static Long elapsedMillis(Object startNanos) {
        return startNanos instanceof Long start ? (System.nanoTime() - start) / 1_000_000 : null;
    }
}
