package com.hjl.oj.ai.model.mock;

import com.hjl.oj.ai.model.ChatModelOptions;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * 模拟模式下按调用用途生成回复。由使用 AI 的业务模块实现，ai 层不认识具体业务。
 */
public interface MockReplyGenerator {

    /**
     * @return 模型的回复正文；options.jsonOutput() 为 true 时必须是合法 JSON
     */
    String reply(ChatModelOptions options, ChatRequest request);

    /**
     * @return 思考模式下先输出的思考过程
     */
    default String thinking(ChatModelOptions options, ChatRequest request) {
        return "（模拟模式）先想一想……";
    }
}
