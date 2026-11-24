package com.hjl.oj.companion.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTranscriptTest {

    @Test
    void keepsOnlyWhatUserSaidAndRecentEvents() {
        String prompt = "【场景】用户在聊天\n\n【当前提示状态】\n- 方向：找思路\n\n【最近的提交与你的反应】\n- 提交结果：超时\n\n"
                + "【用户当前代码】（java）\n```\n1| int a;\n```\n\n【用户说】\n为什么超时？";

        String part = MemoryTranscript.userPart(prompt);

        assertTrue(part.contains("为什么超时？"));
        assertTrue(part.contains("[最近的提交与你的反应] - 提交结果：超时"));
        assertTrue(!part.contains("int a;"));
        assertTrue(!part.contains("当前提示状态"));
    }

    @Test
    void fallsBackToSceneLineWithoutUserText() {
        assertEquals("【场景】用户选择了看答案。", MemoryTranscript.userPart("【场景】用户选择了看答案。\n- 先讲思路"));
    }

    @Test
    void keepsTheMostRecentPartWhenTooLong() {
        List<ChatMessage> messages = List.of(UserMessage.from("【用户说】\n" + "早".repeat(100)), AiMessage.from("最新的回答"));

        String transcript = MemoryTranscript.render(messages, 20);

        assertTrue(transcript.startsWith("……（更早的对话省略）"));
        assertTrue(transcript.endsWith("最新的回答"));
    }

    @Test
    void storeStripsThinkingBeforeSaving() {
        List<ChatMessage> sanitized = MysqlChatMemoryStore.withoutThinking(List.of(
                AiMessage.builder().text("答案").thinking("很长的思考过程").build(), UserMessage.from("问题")));

        assertNull(((AiMessage) sanitized.get(0)).thinking());
        assertEquals("答案", ((AiMessage) sanitized.get(0)).text());
    }
}
