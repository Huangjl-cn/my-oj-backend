package com.hjl.oj.judge.strategy.comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeValueDefinition;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import com.hjl.oj.service.JudgeCaseDataService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JudgeOutputComparatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JudgeOutputComparator comparator = new JudgeOutputComparator(
            objectMapper, new JudgeCaseDataService());

    @Test
    void arrayComparisonUsesJsonValuesInsteadOfRawFormatting() {
        JudgeContext context = context(
                "INTEGER_ARRAY",
                List.of(judgeCase(List.of(1, 2, 3), List.of(1, 2, 3))),
                List.of("[1, 2, 3]"));

        JudgeInfo result = comparator.compare(context, acceptedInfo());

        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(), result.getMessage());
    }

    @Test
    void wrongAnswerContainsOnlyTheFirstFailedCase() {
        JudgeContext context = context(
                "INTEGER",
                List.of(judgeCase(1, 3), judgeCase(5, 11)),
                List.of("4", "12"));

        JudgeInfo result = comparator.compare(context, acceptedInfo());

        assertEquals("Wrong Answer\n用例: 1\n输入:\n[1]\n预期输出:\n3\n实际输出:\n4",
                result.getMessage());
        assertFalse(result.getMessage().contains("11"));
        assertFalse(result.getMessage().contains("12"));
    }

    @Test
    void extraDebugOutputIsWrongAnswer() {
        JudgeContext context = context(
                "INTEGER_ARRAY",
                List.of(judgeCase(List.of(1), List.of(1))),
                List.of("debug\n[1]"));

        JudgeInfo result = comparator.compare(context, acceptedInfo());

        assertEquals("Wrong Answer\n用例: 1\n输入:\n[[1]]\n预期输出:\n[1]\n实际输出:\ndebug\n[1]",
                result.getMessage());
    }

    private JudgeContext context(String outputType, List<JudgeCase> cases, List<String> outputList) {
        JudgeValueDefinition outputDefinition = new JudgeValueDefinition();
        outputDefinition.setType(outputType);
        JudgeCaseConfig config = new JudgeCaseConfig();
        config.setOutputDefinition(outputDefinition);
        config.setCases(cases);

        JudgeContext context = new JudgeContext();
        context.setJudgeCaseConfig(config);
        context.setOutputList(outputList);
        return context;
    }

    private JudgeCase judgeCase(Object input, Object expectedOutput) {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInputs(List.of(objectMapper.valueToTree(input)));
        judgeCase.setExpectedOutput(objectMapper.valueToTree(expectedOutput));
        return judgeCase;
    }

    private JudgeInfo acceptedInfo() {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        return judgeInfo;
    }
}
