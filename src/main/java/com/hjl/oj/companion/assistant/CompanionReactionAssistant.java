package com.hjl.oj.companion.assistant;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 提交后的反应：结构化输出 ReactionAnswer，经输出护栏校验。不挂会话记忆。
 */
public interface CompanionReactionAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{message}}")
    Result<ReactionAnswer> react(@V("systemPrompt") String systemPrompt, @V("message") String message);
}
