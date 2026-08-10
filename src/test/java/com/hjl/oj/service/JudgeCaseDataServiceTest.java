package com.hjl.oj.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeParameterDefinition;
import com.hjl.oj.model.dto.question.JudgeValueDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JudgeCaseDataServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JudgeCaseDataService service = new JudgeCaseDataService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
    }

    @Test
    void validConfigCanRoundTripThroughJson() {
        JudgeCaseConfig config = config(
                List.of(definition("nums", "INTEGER_ARRAY"), definition("target", "INTEGER")),
                "INTEGER_ARRAY",
                List.of(judgeCase(List.of(List.of(2, 7, 11, 15), 9), List.of(0, 1))));

        service.validate(config);
        JudgeCaseConfig restored = service.deserialize(service.validateAndSerialize(config));

        assertEquals("INTEGER_ARRAY", restored.getInputDefinitions().getFirst().getType());
        assertEquals(objectMapper.valueToTree(List.of(0, 1)), restored.getCases().getFirst().getExpectedOutput());
    }

    @Test
    void mismatchedInputTypeIsRejected() {
        JudgeCaseConfig config = config(
                List.of(definition("target", "INTEGER")),
                "INTEGER",
                List.of(judgeCase(List.of("not-an-integer"), 1)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.validate(config));

        assertEquals("第1条用例第1个输入类型错误", exception.getMessage());
    }

    @Test
    void irregularIntegerMatrixIsRejected() {
        JudgeCaseConfig config = config(
                List.of(definition("matrix", "INTEGER_MATRIX")),
                "INTEGER",
                List.of(judgeCase(List.of(List.of(List.of(1, 2), List.of(3))), 1)));

        assertThrows(BusinessException.class, () -> service.validate(config));
    }

    private JudgeCaseConfig config(List<JudgeParameterDefinition> definitions,
                                   String outputType,
                                   List<JudgeCase> cases) {
        JudgeValueDefinition outputDefinition = new JudgeValueDefinition();
        outputDefinition.setType(outputType);
        JudgeCaseConfig config = new JudgeCaseConfig();
        config.setInputDefinitions(definitions);
        config.setOutputDefinition(outputDefinition);
        config.setCases(cases);
        return config;
    }

    private JudgeParameterDefinition definition(String name, String type) {
        JudgeParameterDefinition definition = new JudgeParameterDefinition();
        definition.setName(name);
        definition.setType(type);
        return definition;
    }

    private JudgeCase judgeCase(List<?> inputs, Object expectedOutput) {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInputs(inputs.stream().<JsonNode>map(objectMapper::valueToTree).toList());
        judgeCase.setExpectedOutput(objectMapper.valueToTree(expectedOutput));
        return judgeCase;
    }
}
