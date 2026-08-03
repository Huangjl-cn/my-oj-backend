package com.hjl.oj.judge.strategy;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeConfig;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.ExecuteStatusEnum;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JudgeStrategyTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void executionFailureKeepsSandboxDiagnostic(int executeStatus) {
        JudgeContext context = buildContext(
                executeStatus,
                "sandbox stack trace",
                List.of(),
                List.of(judgeCase("1", "1")),
                0L,
                0L,
                1024L,
                1000L);

        JudgeInfo result = new DefaultJudgeStrategy().evaluate(context);

        assertEquals("sandbox stack trace", result.getMessage());
    }

    @Test
    void wrongAnswerContainsOnlyTheFirstFailedCase() {
        JudgeContext context = buildContext(
                ExecuteStatusEnum.ACCEPTED.getValue(),
                null,
                List.of("4", "12"),
                List.of(judgeCase("1 2", "3"), judgeCase("5 6", "11")),
                128L,
                20L,
                1024L,
                1000L);

        JudgeInfo result = new DefaultJudgeStrategy().evaluate(context);

        assertEquals("Wrong Answer\n用例: 1\n输入:\n1 2\n预期输出:\n3\n实际输出:\n4", result.getMessage());
        assertFalse(result.getMessage().contains("5 6"));
        assertFalse(result.getMessage().contains("11"));
        assertFalse(result.getMessage().contains("12"));
    }

    @Test
    void javaStrategyKeepsItsAdditionalTimeAllowance() {
        JudgeContext context = buildContext(
                ExecuteStatusEnum.ACCEPTED.getValue(),
                null,
                List.of("1"),
                List.of(judgeCase("1", "1")),
                128L,
                2500L,
                1024L,
                1000L);

        JudgeInfo defaultResult = new DefaultJudgeStrategy().evaluate(context);
        JudgeInfo javaResult = new JavaLanguageJudgeStrategy().evaluate(context);

        assertEquals(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue(), defaultResult.getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(), javaResult.getMessage());
    }

    @Test
    void languageStrategiesApplyTheirMemoryAllowance() {
        JudgeContext context = buildContext(
                ExecuteStatusEnum.ACCEPTED.getValue(),
                null,
                List.of("1"),
                List.of(judgeCase("1", "1")),
                30 * 1024L,
                20L,
                16 * 1024L,
                1000L);

        assertEquals(JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED.getValue(),
                new CPlusPlusLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new GoLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new JavaLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new PythonLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new JavaScriptLanguageJudgeStrategy().evaluate(context).getMessage());
    }

    @Test
    void interpretedLanguagesApplyTheirTimeAllowance() {
        JudgeContext context = buildContext(
                ExecuteStatusEnum.ACCEPTED.getValue(),
                null,
                List.of("1"),
                List.of(judgeCase("1", "1")),
                128L,
                1800L,
                1024L,
                1000L);

        assertEquals(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue(),
                new CPlusPlusLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue(),
                new GoLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new JavaLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new PythonLanguageJudgeStrategy().evaluate(context).getMessage());
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(),
                new JavaScriptLanguageJudgeStrategy().evaluate(context).getMessage());
    }

    private JudgeContext buildContext(int executeStatus,
                                      String sandboxMessage,
                                      List<String> outputList,
                                      List<JudgeCase> judgeCases,
                                      long memory,
                                      long time,
                                      long memoryLimit,
                                      long timeLimit) {
        JudgeInfo executeInfo = new JudgeInfo();
        executeInfo.setMessage(sandboxMessage);
        executeInfo.setMemory(memory);
        executeInfo.setTime(time);

        JudgeConfig judgeConfig = new JudgeConfig();
        judgeConfig.setMemoryLimit(memoryLimit);
        judgeConfig.setTimeLimit(timeLimit);
        Question question = new Question();
        question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));

        JudgeContext context = new JudgeContext();
        context.setExecuteStatus(executeStatus);
        context.setJudgeInfo(executeInfo);
        context.setInputList(judgeCases.stream().map(JudgeCase::getInput).toList());
        context.setOutputList(outputList);
        context.setJudgeCaseList(judgeCases);
        context.setQuestion(question);
        return context;
    }

    private JudgeCase judgeCase(String input, String output) {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInput(input);
        judgeCase.setOutput(output);
        return judgeCase;
    }
}
