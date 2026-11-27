package com.hjl.oj.ai.model;

import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.ai.listener.UsageLoggingListener;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 模型：走 OpenAI 兼容接口。
 *
 * <p>DeepSeek 专有的思考模式开关 {@code thinking.type} 通过 customParameters 放进请求体；
 * 开启思考时解析 {@code reasoning_content} 到 AiMessage#thinking()。
 */
public class DeepSeekChatModelProvider implements ChatModelProvider {

    private static final String JSON_OBJECT = "json_object";

    private final AiProperties.DeepSeek properties;

    public DeepSeekChatModelProvider(AiProperties.DeepSeek properties) {
        this.properties = properties;
    }

    @Override
    public ChatModel chatModel(ChatModelOptions options) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(options.apiKey())
                .modelName(options.modelName())
                .timeout(properties.getTimeout())
                .maxRetries(1)
                .customParameters(thinkingParameter(options.thinking()))
                .returnThinking(options.thinking())
                .listeners(listeners(options));
        if (options.jsonOutput()) {
            builder.responseFormat(JSON_OBJECT);
        }
        return builder.build();
    }

    @Override
    public StreamingChatModel streamingChatModel(ChatModelOptions options) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(options.apiKey())
                .modelName(options.modelName())
                .timeout(properties.getTimeout())
                .accumulateToolCallId(false)
                .customParameters(thinkingParameter(options.thinking()))
                .returnThinking(options.thinking())
                .listeners(listeners(options));
        if (options.jsonOutput()) {
            builder.responseFormat(JSON_OBJECT);
        }
        return builder.build();
    }

    /**
     * 先记用量日志，再执行这一次调用额外的监听器
     */
    private static List<ChatModelListener> listeners(ChatModelOptions options) {
        List<ChatModelListener> listeners = new ArrayList<>();
        listeners.add(new UsageLoggingListener(options.purpose()));
        listeners.addAll(options.listeners());
        return listeners;
    }

    private static Map<String, Object> thinkingParameter(boolean thinking) {
        return Map.of("thinking", Map.of("type", thinking ? "enabled" : "disabled"));
    }
}
