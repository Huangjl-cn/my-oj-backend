package com.hjl.oj.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * 按调用选项创建 LangChain4j 模型。每次请求新建：每个用户的 Key、模型和思考模式都可能不同。
 */
public interface ChatModelProvider {

    ChatModel chatModel(ChatModelOptions options);

    StreamingChatModel streamingChatModel(ChatModelOptions options);
}
