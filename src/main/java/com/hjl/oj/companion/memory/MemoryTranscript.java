package com.hjl.oj.companion.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把会话记忆渲染成文字回顾，用于不挂记忆的调用（提示、反应）和记忆压缩。
 *
 * <p>记忆里的用户消息是完整的提示词（场景、状态、代码……），回顾只保留「最近的提交」和「用户说」这两节，
 * 没有这两节时保留第一行场景说明；代码每次都会重新带上，不需要出现在回顾里。
 */
public final class MemoryTranscript {

    public static final String USER_SAYS = "用户说";

    public static final String RECENT_EVENTS = "最近的提交与你的反应";

    private static final Set<String> KEPT_SECTIONS = Set.of(USER_SAYS, RECENT_EVENTS);

    private static final int MAX_TOOL_RESULT_CHARS = 200;

    private MemoryTranscript() {
    }

    /**
     * @param maxChars 超出时保留最近的部分
     */
    public static String render(List<ChatMessage> messages, int maxChars) {
        String full = messages.stream()
                .map(MemoryTranscript::renderMessage)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining("\n"));
        if (full.length() <= maxChars) {
            return full;
        }
        return "……（更早的对话省略）\n" + full.substring(full.length() - maxChars);
    }

    static String renderMessage(ChatMessage message) {
        if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
            return "用户：" + userPart(userMessage.singleText());
        }
        if (message instanceof AiMessage aiMessage) {
            StringBuilder text = new StringBuilder();
            if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                text.append("陪练：").append(aiMessage.text().strip());
            }
            if (aiMessage.hasToolExecutionRequests()) {
                String tools = aiMessage.toolExecutionRequests().stream()
                        .map(request -> request.name())
                        .collect(Collectors.joining("、"));
                text.append(text.isEmpty() ? "" : "\n").append("（陪练调用了工具：").append(tools).append("）");
            }
            return text.toString();
        }
        if (message instanceof ToolExecutionResultMessage result) {
            String output = result.text() == null ? "" : result.text().strip();
            if (output.length() > MAX_TOOL_RESULT_CHARS) {
                output = output.substring(0, MAX_TOOL_RESULT_CHARS) + "……";
            }
            return "（工具 " + result.toolName() + " 返回：" + output + "）";
        }
        return "";
    }

    /**
     * 从完整的用户提示词里取出用户真正说的话和期间的提交
     */
    public static String userPart(String prompt) {
        String[] sections = prompt.split("\n\n(?=【)");
        StringBuilder kept = new StringBuilder();
        for (String section : sections) {
            String title = sectionTitle(section);
            if (title != null && KEPT_SECTIONS.contains(title)) {
                String body = section.substring(section.indexOf('】') + 1).strip();
                kept.append(kept.isEmpty() ? "" : "\n").append(USER_SAYS.equals(title) ? body : "[" + title + "] " + body);
            }
        }
        if (!kept.isEmpty()) {
            return kept.toString();
        }
        int newline = prompt.indexOf('\n');
        return (newline < 0 ? prompt : prompt.substring(0, newline)).strip();
    }

    private static String sectionTitle(String section) {
        if (!section.startsWith("【")) {
            return null;
        }
        int end = section.indexOf('】');
        return end < 0 ? null : section.substring(1, end);
    }
}
