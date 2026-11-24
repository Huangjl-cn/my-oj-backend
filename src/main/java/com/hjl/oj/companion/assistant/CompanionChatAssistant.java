package com.hjl.oj.companion.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 陪练聊天：流式输出，带会话记忆和工具（查提交记录、改代码）。
 *
 * <p>系统提示词和用户消息都只通过变量代入：框架会把模板当作模板渲染，题目或代码里的 "{{" 如果直接作为模板会报错。
 */
public interface CompanionChatAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{message}}")
    TokenStream chat(@V("systemPrompt") String systemPrompt, @V("message") String message);
}
