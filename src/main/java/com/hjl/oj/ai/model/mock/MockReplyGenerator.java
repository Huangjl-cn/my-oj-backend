package com.hjl.oj.ai.model.mock;

import com.hjl.oj.ai.model.ChatModelOptions;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.List;

/**
 * 模拟模式下按调用用途生成回复。由使用 AI 的业务模块实现，ai 层不认识具体业务。
 */
public interface MockReplyGenerator {

    /**
     * @return 模型的回复正文；options.jsonOutput() 为 true 时必须是合法 JSON
     */
    String reply(ChatModelOptions options, ChatRequest request);

    /**
     * 先调用哪些工具：返回非空时这一次只发起工具调用、不出正文，工具结果回来后再调 reply 出正文。
     * 用来在模拟模式下把用到工具的流程跑通，默认不调用工具。
     */
    default List<ToolExecutionRequest> toolCalls(ChatModelOptions options, ChatRequest request) {
        return List.of();
    }

    /**
     * @return 思考模式下先输出的思考过程
     */
    default String thinking(ChatModelOptions options, ChatRequest request) {
        return "（模拟模式）先想一想……";
    }
}
