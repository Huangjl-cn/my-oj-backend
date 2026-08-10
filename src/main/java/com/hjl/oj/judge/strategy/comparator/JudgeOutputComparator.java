package com.hjl.oj.judge.strategy.comparator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import com.hjl.oj.model.enums.JudgeValueTypeEnum;
import com.hjl.oj.service.JudgeCaseDataService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * 按题目声明的输出类型比较结构化预期值和程序标准输出。
 */
@Component
public class JudgeOutputComparator {

    private static final String NO_OUTPUT = "<无输出>";

    private static final double DOUBLE_TOLERANCE = 1e-6;

    private final ObjectMapper objectMapper;

    private final JudgeCaseDataService judgeCaseDataService;

    public JudgeOutputComparator(ObjectMapper objectMapper, JudgeCaseDataService judgeCaseDataService) {
        this.objectMapper = objectMapper;
        this.judgeCaseDataService = judgeCaseDataService;
    }

    public JudgeInfo compare(JudgeContext judgeContext, JudgeInfo result) {
        JudgeCaseConfig config = judgeContext.getJudgeCaseConfig();
        List<JudgeCase> judgeCases = config.getCases();
        List<String> outputList = Optional.ofNullable(judgeContext.getOutputList()).orElse(List.of());
        int comparableSize = Math.min(judgeCases.size(), outputList.size());
        String outputType = config.getOutputDefinition().getType();

        for (int i = 0; i < comparableSize; i++) {
            JudgeCase judgeCase = judgeCases.get(i);
            String actualOutput = outputList.get(i);
            JsonNode actualValue = parseOutput(actualOutput);
            if (!judgeCaseDataService.matches(outputType, actualValue)
                    || !equalsValue(outputType, judgeCase.getExpectedOutput(), actualValue)) {
                result.setMessage(wrongAnswerMessage(i, judgeCase, actualOutput));
                return result;
            }
        }

        if (outputList.size() < judgeCases.size()) {
            int failedIndex = outputList.size();
            result.setMessage(wrongAnswerMessage(failedIndex, judgeCases.get(failedIndex), NO_OUTPUT));
            return result;
        }
        if (outputList.size() > judgeCases.size()) {
            result.setMessage("Wrong Answer\n预期输出数量: " + judgeCases.size()
                    + "\n实际输出数量: " + outputList.size());
            return result;
        }

        result.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        return result;
    }

    private JsonNode parseOutput(String output) {
        if (StringUtils.isBlank(output)) {
            return null;
        }
        try (JsonParser parser = objectMapper.createParser(output)) {
            JsonNode value = objectMapper.readTree(parser);
            if (value == null || parser.nextToken() != null) {
                return null;
            }
            return value;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean equalsValue(String type, JsonNode expected, JsonNode actual) {
        JudgeValueTypeEnum valueType = JudgeValueTypeEnum.getEnumByValue(type);
        if (valueType == null) {
            return false;
        }
        return switch (valueType) {
            case INTEGER -> expected.intValue() == actual.intValue();
            case LONG -> toBigInteger(expected).equals(toBigInteger(actual));
            case DOUBLE -> Math.abs(expected.doubleValue() - actual.doubleValue()) <= DOUBLE_TOLERANCE;
            case BOOLEAN -> expected.booleanValue() == actual.booleanValue();
            case STRING -> expected.textValue().equals(actual.textValue());
            case INTEGER_ARRAY -> equalsIntegerArray(expected, actual);
            case INTEGER_MATRIX -> equalsIntegerMatrix(expected, actual);
            case STRING_ARRAY -> equalsStringArray(expected, actual);
        };
    }

    private BigInteger toBigInteger(JsonNode value) {
        return new BigInteger(value.asText());
    }

    private boolean equalsIntegerArray(JsonNode expected, JsonNode actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).intValue() != actual.get(i).intValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean equalsIntegerMatrix(JsonNode expected, JsonNode actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!equalsIntegerArray(expected.get(i), actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean equalsStringArray(JsonNode expected, JsonNode actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).textValue().equals(actual.get(i).textValue())) {
                return false;
            }
        }
        return true;
    }

    private String wrongAnswerMessage(int index, JudgeCase judgeCase, String actualOutput) {
        return "Wrong Answer\n用例: " + (index + 1)
                + "\n输入:\n" + judgeCase.getInputs()
                + "\n预期输出:\n" + judgeCase.getExpectedOutput()
                + "\n实际输出:\n" + StringUtils.defaultIfBlank(actualOutput, NO_OUTPUT);
    }
}
