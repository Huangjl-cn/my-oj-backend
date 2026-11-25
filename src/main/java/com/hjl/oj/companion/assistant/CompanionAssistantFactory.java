package com.hjl.oj.companion.assistant;

import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.ai.model.ChatModelOptions;
import com.hjl.oj.ai.model.ChatModelProvider;
import com.hjl.oj.companion.guardrail.HintAnswerGuardrail;
import com.hjl.oj.companion.guardrail.ReactionAnswerGuardrail;
import com.hjl.oj.companion.memory.ConversationMemoryId;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按请求组装陪练的各个 AI Service：每个用户的 Key、模型、记忆、工具和改代码权限都不同，所以不做成单例。
 */
@Component
public class CompanionAssistantFactory {

    /**
     * 记忆的安全上限：正常情况下靠压缩控制长度，这里只防止异常情况下无限增长
     */
    static final int MAX_MEMORY_MESSAGES = 1000;

    /**
     * 一轮回答里模型连续调用工具的上限
     */
    static final int MAX_SEQUENTIAL_TOOL_CALLS = 8;

    /**
     * 输出护栏：第一次回答加一次重答
     */
    static final int GUARDRAIL_MAX_ATTEMPTS = 2;

    private final ChatModelProvider chatModelProvider;

    private final MysqlChatMemoryStore chatMemoryStore;

    public CompanionAssistantFactory(ChatModelProvider chatModelProvider, MysqlChatMemoryStore chatMemoryStore) {
        this.chatModelProvider = chatModelProvider;
        this.chatMemoryStore = chatMemoryStore;
    }

    /**
     * 对话记忆：系统消息始终放在最前（利于前缀缓存），读写都直接落到数据库
     */
    public ChatMemory conversationMemory(ConversationMemoryId memoryId) {
        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .chatMemoryStore(chatMemoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }

    public CompanionChatAssistant chatAssistant(AiCredential credential, ChatMemory memory, List<Object> tools) {
        return AiServices.builder(CompanionChatAssistant.class)
                .streamingChatModel(chatModelProvider.streamingChatModel(
                        options(credential, CompanionPurpose.CHAT, false, false)))
                .chatMemory(memory)
                .tools(tools)
                .maxSequentialToolsInvocations(MAX_SEQUENTIAL_TOOL_CALLS)
                .hallucinatedToolNameStrategy(request ->
                        ToolExecutionResultMessage.from(request, "没有名为 " + request.name() + " 的工具，请只使用提供的工具"))
                // 参数写错时把原因告诉模型让它重试；工具内部的意外异常（如数据库错误）不发给模型，直接结束本轮
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
                .build();
    }

    public CompanionSolutionAssistant solutionAssistant(AiCredential credential, ChatMemory memory) {
        return AiServices.builder(CompanionSolutionAssistant.class)
                .streamingChatModel(chatModelProvider.streamingChatModel(
                        options(credential, CompanionPurpose.SOLUTION, true, false)))
                .chatMemory(memory)
                .build();
    }

    public CompanionHintAssistant hintAssistant(AiCredential credential) {
        return AiServices.builder(CompanionHintAssistant.class)
                .chatModel(chatModelProvider.chatModel(options(credential, CompanionPurpose.HINT, false, true)))
                .outputGuardrails(new HintAnswerGuardrail())
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder().maxRetries(GUARDRAIL_MAX_ATTEMPTS).build())
                .build();
    }

    public CompanionReactionAssistant reactionAssistant(AiCredential credential) {
        return AiServices.builder(CompanionReactionAssistant.class)
                .chatModel(chatModelProvider.chatModel(options(credential, CompanionPurpose.REACTION, false, true)))
                .outputGuardrails(new ReactionAnswerGuardrail())
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder().maxRetries(GUARDRAIL_MAX_ATTEMPTS).build())
                .build();
    }

    public CompanionCompactionAssistant compactionAssistant(AiCredential credential) {
        return AiServices.builder(CompanionCompactionAssistant.class)
                .chatModel(chatModelProvider.chatModel(options(credential, CompanionPurpose.COMPACTION, false, false)))
                .build();
    }

    private static ChatModelOptions options(AiCredential credential, String purpose, boolean thinking,
                                            boolean jsonOutput) {
        return new ChatModelOptions(credential.apiKey(), credential.modelName(), purpose, thinking, jsonOutput);
    }
}
