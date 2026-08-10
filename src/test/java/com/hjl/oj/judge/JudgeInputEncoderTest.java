package com.hjl.oj.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjl.oj.judge.codesandbox.model.ExecuteCaseRequest;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeParameterDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeInputEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void eachLogicalInputKeepsItsOwnArgumentBoundary() {
        JudgeCaseConfig config = new JudgeCaseConfig();
        config.setInputDefinitions(List.of(
                definition("label", "STRING"),
                definition("nums", "INTEGER_ARRAY"),
                definition("matrix", "INTEGER_MATRIX")));
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInputs(List.of(
                objectMapper.valueToTree("hello world"),
                objectMapper.valueToTree(List.of(1, 2, 3)),
                objectMapper.valueToTree(List.of(List.of(1, 2), List.of(3, 4)))));
        config.setCases(List.of(judgeCase));

        List<ExecuteCaseRequest> result = new JudgeInputEncoder().encode(config);

        assertEquals(List.of("hello world", "[1,2,3]", "[[1,2],[3,4]]"),
                result.getFirst().getArgs());
    }

    private JudgeParameterDefinition definition(String name, String type) {
        JudgeParameterDefinition definition = new JudgeParameterDefinition();
        definition.setName(name);
        definition.setType(type);
        return definition;
    }
}
