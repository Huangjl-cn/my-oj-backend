package com.hjl.oj.companion.prompt;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.companion.context.SubmissionFacts;
import com.hjl.oj.companion.hint.EditPermission;
import com.hjl.oj.companion.hint.HintLadder;
import com.hjl.oj.companion.hint.HintState;
import com.hjl.oj.model.dto.companion.CodeQuote;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.dto.question.JudgeConfig;
import com.hjl.oj.model.entity.Question;
import dev.langchain4j.model.input.PromptTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 陪练的提示词：模板放在 resources/prompts/companion/，这里负责渲染和拼装。
 *
 * <p>所有场景共用同一份系统提示词（人设、分级规则、题目信息），同一道题下的调用开头完全一致，便于命中 DeepSeek 的前缀缓存；
 * 场景要求、当前状态、代码这些会变的内容放在用户消息里。
 */
@Component
public class CompanionPrompts {

    private static final String TEMPLATE_DIR = "prompts/companion/";

    private static final String NONE = "无";

    private final PromptTemplate systemTemplate;

    private final PromptTemplate chatScene;

    private final PromptTemplate hintScene;

    private final String reactionScene;

    private final PromptTemplate solutionScene;

    private final PromptTemplate compactionScene;

    private final AiProperties aiProperties;

    public CompanionPrompts(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.systemTemplate = PromptTemplate.from(load("system.md"));
        this.chatScene = PromptTemplate.from(load("scene-chat.md"));
        this.hintScene = PromptTemplate.from(load("scene-hint.md"));
        this.reactionScene = load("scene-reaction.md");
        this.solutionScene = PromptTemplate.from(load("scene-solution.md"));
        this.compactionScene = PromptTemplate.from(load("scene-compaction.md"));
    }

    public String systemMessage(Question question) {
        JudgeConfig judgeConfig = judgeConfig(question);
        return systemTemplate.apply(Map.<String, Object>of(
                "personaName", aiProperties.getCompanion().getPersonaName(),
                "hintLadder", HintLadder.describeForPrompt(),
                "questionTitle", StringUtils.defaultIfBlank(question.getTitle(), NONE),
                "timeLimit", judgeConfig.getTimeLimit() == null ? "未知" : judgeConfig.getTimeLimit(),
                "memoryLimit", judgeConfig.getMemoryLimit() == null ? "未知" : judgeConfig.getMemoryLimit(),
                "questionContent", StringUtils.defaultIfBlank(question.getContent(), NONE),
                "officialAnswer", StringUtils.defaultIfBlank(question.getAnswer(), NONE))).text();
    }

    /**
     * @param quote 用户选中代码后提问时引用的那段，可为空
     */
    public String chatMessage(HintState state, CodeSnapshot snapshot, String recentEvents, CodeQuote quote,
                              String userText) {
        String rewriteNote = state.editPermission() == EditPermission.UNRESTRICTED ? "，需要整体替换时调用 rewriteCode" : "";
        return join(chatScene.apply(Map.<String, Object>of("rewriteNote", rewriteNote)).text(),
                state.describe(),
                recentEvents,
                renderCode(snapshot),
                renderQuote(quote),
                "【用户说】\n" + userText);
    }

    public String hintMessage(HintState state, CodeSnapshot snapshot, String recentEvents, String transcript) {
        return join(hintScene.apply(Map.<String, Object>of("level", state.effectiveLevel())).text(),
                state.describe(),
                recentEvents,
                section("对话回顾", transcript),
                renderCode(snapshot));
    }

    public String reactionMessage(HintState state, SubmissionFacts facts, String submittedCode, String language,
                                  List<String> recentLines, String transcript) {
        CodeSnapshot submitted = new CodeSnapshot();
        submitted.setCode(submittedCode);
        submitted.setLanguage(language);
        String recent = recentLines.isEmpty() ? NONE : "- " + String.join("\n- ", recentLines);
        return join(reactionScene,
                state.describe(),
                "【提交事实】\n" + facts.describe(),
                section("你最近说过的话", recent),
                section("对话回顾", transcript),
                renderCode(submitted).replace("【用户当前代码】", "【这次提交的代码】"));
    }

    public String solutionMessage(HintState state, CodeSnapshot snapshot) {
        String language = snapshot == null ? "" : StringUtils.defaultString(snapshot.getLanguage());
        return join(solutionScene.apply(Map.<String, Object>of("language", language)).text(),
                state.withLevel(HintLadder.SOLUTION_LEVEL).describe(),
                renderCode(snapshot));
    }

    public String compactionMessage(String previousNote, String transcript) {
        return compactionScene.apply(Map.<String, Object>of(
                "previousNote", StringUtils.defaultIfBlank(previousNote, NONE),
                "transcript", StringUtils.defaultIfBlank(transcript, NONE))).text();
    }

    /**
     * 带行号的代码，以及光标和选区。超长时截断并说明。
     */
    String renderCode(CodeSnapshot snapshot) {
        if (snapshot == null || StringUtils.isBlank(snapshot.getCode())) {
            return "【用户当前代码】\n（编辑器是空的）";
        }
        String code = snapshot.getCode().replace("\r\n", "\n");
        int maxChars = aiProperties.getCompanion().getMaxCodeChars();
        boolean truncated = code.length() > maxChars;
        if (truncated) {
            code = code.substring(0, maxChars);
        }
        String[] lines = code.split("\n", -1);
        int width = String.valueOf(lines.length).length();
        StringBuilder text = new StringBuilder("【用户当前代码】（").append(StringUtils.defaultString(snapshot.getLanguage()))
                .append("，行号只用于定位，不属于代码）\n```\n");
        for (int i = 0; i < lines.length; i++) {
            text.append(StringUtils.leftPad(String.valueOf(i + 1), width)).append("| ").append(lines[i]).append("\n");
        }
        text.append("```\n");
        if (truncated) {
            text.append("（代码过长，只展示了前 ").append(maxChars).append(" 个字符）\n");
        }
        if (snapshot.getSelectionStartLine() != null && snapshot.getSelectionEndLine() != null) {
            text.append("用户选中了第 ").append(snapshot.getSelectionStartLine()).append(" 到第 ")
                    .append(snapshot.getSelectionEndLine()).append(" 行，提问很可能就是指这部分\n");
        } else if (snapshot.getCursorLine() != null) {
            text.append("光标在第 ").append(snapshot.getCursorLine()).append(" 行\n");
        }
        return text.toString();
    }

    static String renderQuote(CodeQuote quote) {
        if (quote == null || StringUtils.isBlank(quote.getCode())) {
            return "";
        }
        return "【用户引用的代码】第 " + quote.getStartLine() + " 到第 " + quote.getEndLine()
                + " 行，用户这句话问的就是这一段：\n```\n" + quote.getCode().replace("\r\n", "\n").stripTrailing() + "\n```";
    }

    private static JudgeConfig judgeConfig(Question question) {
        String json = question.getJudgeConfig();
        if (StringUtils.isBlank(json) || !JSONUtil.isTypeJSONObject(json)) {
            return new JudgeConfig();
        }
        return JSONUtil.toBean(json, JudgeConfig.class);
    }

    private static String section(String title, String content) {
        return "【" + title + "】\n" + StringUtils.defaultIfBlank(content, NONE);
    }

    private static String join(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                if (!text.isEmpty()) {
                    text.append("\n\n");
                }
                text.append(part.strip());
            }
        }
        return text.toString();
    }

    private static String load(String name) {
        try {
            return new ClassPathResource(TEMPLATE_DIR + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("缺少提示词模板 " + TEMPLATE_DIR + name, e);
        }
    }

    public static JudgeConfig parseJudgeConfig(Question question) {
        return judgeConfig(question);
    }
}
