package com.hjl.oj.companion.assistant;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 分级提示：结构化输出 HintAnswer，经输出护栏校验。不挂会话记忆，对话回顾由调用方放进用户消息。
 */
public interface CompanionHintAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{message}}")
    Result<HintAnswer> hint(@V("systemPrompt") String systemPrompt, @V("message") String message);
}
