package com.hjl.oj.companion.mock;

import com.hjl.oj.ai.model.ChatModelOptions;
import com.hjl.oj.ai.model.mock.MockReplyGenerator;
import com.hjl.oj.companion.assistant.CompanionPurpose;
import com.hjl.oj.model.enums.CompanionMoodEnum;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟模式下陪练各场景的固定回复，保证不配任何 Key 也能把流程跑通。
 */
@Component
public class CompanionMockReplies implements MockReplyGenerator {

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
            default -> "（模拟模式）我现在接的是模拟模型，只会说固定的话。设置 AI_MODE=live 并在页面上保存你的 DeepSeek Key，"
                    + "我就能真正看你的代码、陪你想题了。";
        };
    }
}
