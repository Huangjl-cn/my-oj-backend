package com.hjl.oj.companion.mock;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.ai.model.ChatModelOptions;
import com.hjl.oj.ai.model.mock.MockReplyGenerator;
import com.hjl.oj.companion.assistant.CompanionPurpose;
import com.hjl.oj.model.enums.CompanionMoodEnum;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 模拟模式下陪练各场景的固定回复，保证不配任何 Key 也能把流程跑通。
 *
 * <p>聊天时说到「笔记」会真的调用笔记工具：想整理、存进笔记就提议新建一篇，否则列出本题的笔记，
 * 这样不接真实模型也能看到笔记提议的预览和应用。
 */
@Component
public class CompanionMockReplies implements MockReplyGenerator {

    static final String CREATE_NOTE = "createNote";

    static final String LIST_NOTES = "listNotesOnThisQuestion";

    private static final String USER_SAID = "【用户说】";

    private static final Pattern WANTS_NEW_NOTE = Pattern.compile("整理|存|记下|写|新建|总结");

    private static final String MOCK_NOTE = """
            ## 题目要点
            （模拟模式）这里会记下题意里容易忽略的条件。

            ## 思路
            （模拟模式）这里会按对话里说到的程度记思路；还没到「看答案」时不会写解法代码。

            ## 踩过的坑
            - （模拟模式）这里会记下提交出错的原因。

            ## 待复习
            - [ ] 过几天再独立做一遍""";

    @Override
    public String reply(ChatModelOptions options, ChatRequest request) {
        return switch (options.purpose()) {
            case CompanionPurpose.REACTION -> {
                CompanionMoodEnum[] moods = CompanionMoodEnum.values();
                CompanionMoodEnum mood = moods[ThreadLocalRandom.current().nextInt(moods.length)];
                yield "{\"mood\":\"" + mood.name() + "\",\"intensity\":2,\"track\":\"IDEA\","
                        + "\"message\":\"（模拟模式）结果出来了！接上真实模型后，我会根据你的提交认真说两句。\"}";
            }
            case CompanionPurpose.HINT -> "{\"track\":\"IDEA\",\"message\":\"（模拟模式）这是一条模拟提示。"
                    + "设置 AI_MODE=live 并保存 DeepSeek Key 后，提示会针对你的代码来给。\",\"edits\":[]}";
            case CompanionPurpose.SOLUTION -> "（模拟模式）这里会先讲思路和复杂度，再给出参考代码：\n\n"
                    + "```\n// 模拟模式下没有真实的参考代码\n```\n\n最后说明和你原来写法的区别。";
            case CompanionPurpose.COMPACTION -> "## 解题进展\n（模拟模式）无\n\n## 已经给过的提示\n无\n\n## 代码演变\n无\n\n"
                    + "## 提交记录要点\n无\n\n## 用户的偏好和状态\n无\n\n## 没说完的事\n无";
            default -> lastMessage(request) instanceof ToolExecutionResultMessage result
                    ? afterTool(result)
                    : "（模拟模式）我现在接的是模拟模型，只会说固定的话。设置 AI_MODE=live 并在页面上保存你的 DeepSeek Key，"
                    + "我就能真正看你的代码、陪你想题了。";
        };
    }

    @Override
    public List<ToolExecutionRequest> toolCalls(ChatModelOptions options, ChatRequest request) {
        if (!CompanionPurpose.CHAT.equals(options.purpose())
                || !(lastMessage(request) instanceof UserMessage user) || !user.hasSingleText()) {
            return List.of();
        }
        String said = userSaid(user.singleText());
        if (!said.contains("笔记")) {
            return List.of();
        }
        Set<String> tools = request.toolSpecifications() == null ? Set.of() : request.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        if (tools.contains(CREATE_NOTE) && WANTS_NEW_NOTE.matcher(said).find()) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("title", "（模拟）这段对话的笔记");
            arguments.put("content", MOCK_NOTE);
            arguments.put("linkToThisQuestion", true);
            arguments.put("reason", "把这段对话整理成一篇笔记（模拟模式下是固定内容）");
            return List.of(call(CREATE_NOTE, JSONUtil.toJsonStr(arguments)));
        }
        if (tools.contains(LIST_NOTES)) {
            return List.of(call(LIST_NOTES, "{}"));
        }
        return List.of();
    }

    private static String afterTool(ToolExecutionResultMessage result) {
        if (CREATE_NOTE.equals(result.toolName())) {
            return result.text().startsWith("已记录")
                    ? "（模拟模式）我把这段对话整理成了一篇笔记，点下面的卡片预览一下，满意就点「应用」。"
                    : "（模拟模式）笔记没整理成：" + result.text();
        }
        return "（模拟模式）我翻了翻你在这题的笔记：\n\n" + result.text();
    }

    private static ToolExecutionRequest call(String name, String arguments) {
        return ToolExecutionRequest.builder().id("mock-" + name).name(name).arguments(arguments).build();
    }

    private static ChatMessage lastMessage(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        return messages == null || messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * 用户消息里拼了场景说明、代码等，只取最后用户说的那句
     */
    private static String userSaid(String text) {
        int index = text.lastIndexOf(USER_SAID);
        return index < 0 ? text : text.substring(index + USER_SAID.length());
    }
}
