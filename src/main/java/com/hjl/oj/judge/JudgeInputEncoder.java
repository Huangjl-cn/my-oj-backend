package com.hjl.oj.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.model.ExecuteCaseRequest;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeParameterDefinition;
import com.hjl.oj.model.enums.JudgeValueTypeEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将结构化题目输入编码为沙箱可原样传递的进程参数。
 */
@Component
public class JudgeInputEncoder {

    public List<ExecuteCaseRequest> encode(JudgeCaseConfig config) {
        List<JudgeParameterDefinition> definitions = config.getInputDefinitions();
        List<ExecuteCaseRequest> executeCases = new ArrayList<>(config.getCases().size());
        for (JudgeCase judgeCase : config.getCases()) {
            List<String> args = new ArrayList<>(definitions.size());
            for (int i = 0; i < definitions.size(); i++) {
                args.add(encodeValue(definitions.get(i).getType(), judgeCase.getInputs().get(i)));
            }
            executeCases.add(ExecuteCaseRequest.builder().args(args).build());
        }
        return executeCases;
    }

    private String encodeValue(String type, JsonNode value) {
        JudgeValueTypeEnum valueType = JudgeValueTypeEnum.getEnumByValue(type);
        if (valueType == null || value == null || value.isNull()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题输入类型错误");
        }
        return switch (valueType) {
            case INTEGER, LONG, DOUBLE, BOOLEAN -> value.asText();
            case STRING -> value.textValue();
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> value.toString();
        };
    }
}
