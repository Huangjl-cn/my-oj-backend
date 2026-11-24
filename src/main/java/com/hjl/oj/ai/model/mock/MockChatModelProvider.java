package com.hjl.oj.ai.model.mock;

import com.hjl.oj.ai.model.ChatModelOptions;
import com.hjl.oj.ai.model.ChatModelProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * 模拟模型：不访问网络，回复内容来自 MockReplyGenerator。不会发起工具调用。
 */
public class MockChatModelProvider implements ChatModelProvider {

    private static final String MOCK_MODEL_NAME = "mock";

    private static final int STREAM_CHUNK_CHARS = 8;

    private final MockReplyGenerator replyGenerator;

    public MockChatModelProvider(MockReplyGenerator replyGenerator) {
        this.replyGenerator = replyGenerator;
    }

    @Override
    public ChatModel chatModel(ChatModelOptions options) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                String reply = replyGenerator.reply(options, chatRequest);
                AiMessage.Builder message = AiMessage.builder().text(reply);
                if (options.thinking()) {
                    message.thinking(replyGenerator.thinking(options, chatRequest));
                }
                return response(message.build(), chatRequest);
            }
        };
    }

    @Override
    public StreamingChatModel streamingChatModel(ChatModelOptions options) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                try {
                    AiMessage.Builder message = AiMessage.builder();
                    if (options.thinking()) {
                        String thinking = replyGenerator.thinking(options, chatRequest);
                        handler.onPartialThinking(new PartialThinking(thinking));
                        message.thinking(thinking);
                    }
                    String reply = replyGenerator.reply(options, chatRequest);
                    for (int start = 0; start < reply.length(); start += STREAM_CHUNK_CHARS) {
                        handler.onPartialResponse(reply.substring(start, Math.min(reply.length(), start + STREAM_CHUNK_CHARS)));
                    }
                    handler.onCompleteResponse(response(message.text(reply).build(), chatRequest));
                } catch (RuntimeException e) {
                    handler.onError(e);
                }
            }
        };
    }

    private static ChatResponse response(AiMessage message, ChatRequest request) {
        // 粗略估算 token：按序列化后的字符数折算，只用于展示上下文占用
        int inputTokens = ChatMessageSerializer.messagesToJson(request.messages()).length() / 3;
        int outputTokens = message.text() == null ? 0 : message.text().length() / 2;
        return ChatResponse.builder()
                .aiMessage(message)
                .modelName(MOCK_MODEL_NAME)
                .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                .finishReason(FinishReason.STOP)
                .build();
    }
}
