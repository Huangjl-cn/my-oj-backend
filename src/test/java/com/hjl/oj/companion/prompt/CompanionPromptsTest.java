package com.hjl.oj.companion.prompt;

import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.companion.hint.HintState;
import com.hjl.oj.model.dto.companion.CodeQuote;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionPromptsTest {

    private final AiProperties properties = properties();

    private final CompanionPrompts prompts = new CompanionPrompts(properties);

    @Test
    void systemMessageInsertsQuestionContentVerbatimEvenWithTemplateBraces() {
        Question question = new Question();
        question.setTitle("矩阵求和");
        question.setContent("输入 {{1,2},{3,4}}，输出 $10");
        question.setAnswer("vector<vector<int>> m = {{1,2},{3,4}};");
        question.setJudgeConfig("{\"timeLimit\":1000,\"memoryLimit\":65536}");

        String system = prompts.systemMessage(question);

        assertTrue(system.contains("「鸭鸭」"));
        assertTrue(system.contains("输入 {{1,2},{3,4}}，输出 $10"));
        assertTrue(system.contains("{{1,2},{3,4}};"));
        assertTrue(system.contains("时间限制：1000 ms；内存限制：65536 KB"));
        assertTrue(system.contains("「说方法」") && system.contains("反例"));
    }

    @Test
    void chatMessageCarriesStateCodeWithLineNumbersAndUserText() {
        CodeSnapshot snapshot = new CodeSnapshot();
        snapshot.setCode("int a = 1;\r\nint b = 2;");
        snapshot.setLanguage("java");
        snapshot.setSelectionStartLine(2);
        snapshot.setSelectionEndLine(2);
        HintState state = new HintState(HintTrackEnum.BUG, 3, HintModeEnum.GUIDED);

        String message = prompts.chatMessage(state, snapshot, "", null, "这行有问题吗？");

        assertTrue(message.startsWith("【场景】"));
        assertTrue(message.contains("「说方法」") && message.contains("反例"));
        assertTrue(message.contains("1| int a = 1;\n2| int b = 2;"));
        assertTrue(message.contains("选中了第 2 到第 2 行"));
        assertTrue(message.endsWith("【用户说】\n这行有问题吗？"));
        assertTrue(!message.contains("rewriteCode"));
    }

    @Test
    void unrestrictedLevelMentionsRewriteTool() {
        HintState state = new HintState(HintTrackEnum.IDEA, 0, HintModeEnum.DIRECT);

        assertTrue(prompts.chatMessage(state, new CodeSnapshot(), "", null, "直接告诉我").contains("rewriteCode"));
    }

    @Test
    void quotedCodeComesRightBeforeUserText() {
        CodeSnapshot snapshot = new CodeSnapshot();
        snapshot.setCode("int a = 1;\nint b = a + 1;");
        CodeQuote quote = new CodeQuote();
        quote.setStartLine(2);
        quote.setEndLine(2);
        quote.setCode("a + 1\r\n");
        HintState state = new HintState(HintTrackEnum.IDEA, 1, HintModeEnum.GUIDED);

        String message = prompts.chatMessage(state, snapshot, "", quote, "这里会溢出吗？");

        assertTrue(message.endsWith("【用户引用的代码】第 2 到第 2 行，用户这句话问的就是这一段：\n```\na + 1\n```"
                + "\n\n【用户说】\n这里会溢出吗？"));
        assertEquals("", CompanionPrompts.renderQuote(null));
    }

    @Test
    void longCodeIsTruncated() {
        CodeSnapshot snapshot = new CodeSnapshot();
        snapshot.setCode("x".repeat(properties.getCompanion().getMaxCodeChars() + 10));

        assertTrue(prompts.renderCode(snapshot).contains("代码过长"));
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getCompanion().setPersonaName("鸭鸭");
        properties.getCompanion().setMaxCodeChars(100);
        return properties;
    }
}
