package com.hjl.oj.companion.assistant;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 记忆压缩：把较早的对话整理成解题笔记。不挂会话记忆。
 */
public interface CompanionCompactionAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{message}}")
    Result<String> compact(@V("systemPrompt") String systemPrompt, @V("message") String message);
}
