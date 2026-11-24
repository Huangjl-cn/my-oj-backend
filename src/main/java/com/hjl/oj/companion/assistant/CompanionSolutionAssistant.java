package com.hjl.oj.companion.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 参考解法：开思考模式、流式输出，带会话记忆，不带工具（DeepSeek 思考模式加工具调用需要回传思考内容，这里不需要工具）。
 */
public interface CompanionSolutionAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{message}}")
    TokenStream solve(@V("systemPrompt") String systemPrompt, @V("message") String message);
}
