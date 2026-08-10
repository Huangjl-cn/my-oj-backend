package com.hjl.oj.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeParameterDefinition;
import com.hjl.oj.model.dto.question.JudgeValueDefinition;
import com.hjl.oj.model.enums.JudgeValueTypeEnum;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构化判题用例的序列化和业务校验。
 */
@Service
public class JudgeCaseDataService {

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 校验结构化用例后，序列化为题目表中的 JSON 文本。
     */
    public String validateAndSerialize(JudgeCaseConfig config) {
        validate(config);
        try {
            return objectMapper.writeValueAsString(config);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题用例格式错误");
        }
    }

    /**
     * 从题目表中的 JSON 文本读取结构化用例。
     */
    public JudgeCaseConfig deserialize(String json) {
        if (StringUtils.isBlank(json)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题用例不存在");
        }
        try {
            return objectMapper.readValue(json, JudgeCaseConfig.class);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题用例格式错误");
        }
    }

    /**
     * 校验题目级输入定义、用例输入和预期输出。
     */
    public void validate(JudgeCaseConfig config) {
        ThrowUtils.throwIf(config == null, ErrorCode.PARAMS_ERROR, "判题用例不能为空");

        List<JudgeParameterDefinition> inputDefinitions = config.getInputDefinitions();
        ThrowUtils.throwIf(inputDefinitions == null, ErrorCode.PARAMS_ERROR, "输入参数定义不能为空");
        Set<String> names = new HashSet<>();
        for (JudgeParameterDefinition definition : inputDefinitions) {
            ThrowUtils.throwIf(definition == null || StringUtils.isBlank(definition.getName()),
                    ErrorCode.PARAMS_ERROR, "输入参数名称不能为空");
            ThrowUtils.throwIf(!names.add(definition.getName()),
                    ErrorCode.PARAMS_ERROR, "输入参数名称不能重复");
            requireType(definition.getType(), "输入参数类型错误");
        }

        JudgeValueDefinition outputDefinition = config.getOutputDefinition();
        ThrowUtils.throwIf(outputDefinition == null, ErrorCode.PARAMS_ERROR, "输出定义不能为空");
        requireType(outputDefinition.getType(), "输出类型错误");

        List<JudgeCase> cases = config.getCases();
        ThrowUtils.throwIf(cases == null || cases.isEmpty(), ErrorCode.PARAMS_ERROR, "判题用例不能为空");
        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            JudgeCase judgeCase = cases.get(caseIndex);
            ThrowUtils.throwIf(judgeCase == null || judgeCase.getInputs() == null,
                    ErrorCode.PARAMS_ERROR, "第" + (caseIndex + 1) + "条用例输入不能为空");
            ThrowUtils.throwIf(judgeCase.getInputs().size() != inputDefinitions.size(),
                    ErrorCode.PARAMS_ERROR, "第" + (caseIndex + 1) + "条用例输入数量错误");
            for (int inputIndex = 0; inputIndex < inputDefinitions.size(); inputIndex++) {
                String type = inputDefinitions.get(inputIndex).getType();
                ThrowUtils.throwIf(!matches(type, judgeCase.getInputs().get(inputIndex)),
                        ErrorCode.PARAMS_ERROR,
                        "第" + (caseIndex + 1) + "条用例第" + (inputIndex + 1) + "个输入类型错误");
            }
            ThrowUtils.throwIf(!matches(outputDefinition.getType(), judgeCase.getExpectedOutput()),
                    ErrorCode.PARAMS_ERROR, "第" + (caseIndex + 1) + "条用例预期输出类型错误");
        }
    }

    /**
     * 判断 JSON 值是否符合指定的判题类型。
     */
    public boolean matches(String type, JsonNode value) {
        JudgeValueTypeEnum valueType = JudgeValueTypeEnum.getEnumByValue(type);
        if (valueType == null || value == null || value.isNull()) {
            return false;
        }
        return switch (valueType) {
            case INTEGER -> value.isIntegralNumber() && value.canConvertToInt();
            case LONG -> (value.isIntegralNumber() || value.isTextual()) && isLong(value);
            case DOUBLE -> value.isNumber() && Double.isFinite(value.doubleValue());
            case BOOLEAN -> value.isBoolean();
            case STRING -> value.isTextual();
            case INTEGER_ARRAY -> isArrayOf(value, this::isInteger);
            case INTEGER_MATRIX -> isMatrixOf(value, this::isInteger);
            case STRING_ARRAY -> isArrayOf(value, JsonNode::isTextual);
        };
    }

    private void requireType(String type, String message) {
        ThrowUtils.throwIf(JudgeValueTypeEnum.getEnumByValue(type) == null,
                ErrorCode.PARAMS_ERROR, message);
    }

    private boolean isInteger(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToInt();
    }

    private boolean isLong(JsonNode value) {
        try {
            if (value.isIntegralNumber()) {
                return value.canConvertToLong();
            } else {
                Long.parseLong(value.textValue());
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isArrayOf(JsonNode value, java.util.function.Predicate<JsonNode> predicate) {
        if (!value.isArray()) {
            return false;
        }
        for (JsonNode item : value) {
            if (!predicate.test(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean isMatrixOf(JsonNode value, java.util.function.Predicate<JsonNode> predicate) {
        if (!value.isArray()) {
            return false;
        }
        Integer columnCount = null;
        for (JsonNode row : value) {
            if (!row.isArray()) {
                return false;
            }
            if (columnCount == null) {
                columnCount = row.size();
            } else if (columnCount != row.size()) {
                return false;
            }
            if (!isArrayOf(row, predicate)) {
                return false;
            }
        }
        return true;
    }
}
