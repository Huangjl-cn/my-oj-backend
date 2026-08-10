package com.hjl.oj.utils;

import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeParameterDefinition;
import com.hjl.oj.model.dto.question.JudgeValueDefinition;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionStarterCodeDefaultsTest {

    private final JudgeCaseConfig config = config();

    @Test
    void javaTemplateUsesGsonForStructuredInputAndOutput() {
        String code = QuestionStarterCodeDefaults.getDefaultStarterCode(
                QuestionSubmitLanguageEnum.JAVA, config);

        assertTrue(code.contains("import com.google.gson.Gson;"));
        assertTrue(code.contains("int[] numsInput0 = JSON.fromJson(args[0], int[].class);"));
        assertTrue(code.contains("int targetInput1 = Integer.parseInt(args[1]);"));
        assertTrue(code.contains("List<Integer> answer = new ArrayList<>();"));
        assertTrue(code.contains("System.out.print(JSON.toJson(answer));"));
    }

    @Test
    void javaArrayOutputsUseGrowableCollections() {
        assertTrue(javaOutputCode("INTEGER_MATRIX")
                .contains("List<List<Integer>> answer = new ArrayList<>();"));
        assertTrue(javaOutputCode("STRING_ARRAY")
                .contains("List<String> answer = new ArrayList<>();"));
    }

    @Test
    void cppTemplateUsesNlohmannJsonForStructuredInputAndOutput() {
        String code = QuestionStarterCodeDefaults.getDefaultStarterCode(
                QuestionSubmitLanguageEnum.CPLUSPLUS, config);

        assertTrue(code.contains("#include <nlohmann/json.hpp>"));
        assertTrue(code.contains("json::parse(argv[1]).get<std::vector<int>>()"));
        assertTrue(code.contains("int targetInput1 = std::stoi(argv[2]);"));
        assertTrue(code.contains("std::vector<int> answer = {};"));
        assertTrue(code.contains("std::cout << json(answer).dump();"));
    }

    @Test
    void standardLibraryLanguagesParseAndSerializeJson() {
        String goCode = QuestionStarterCodeDefaults.getDefaultStarterCode(
                QuestionSubmitLanguageEnum.GOLANG, config);
        String pythonCode = QuestionStarterCodeDefaults.getDefaultStarterCode(
                QuestionSubmitLanguageEnum.PYTHON, config);
        String javascriptCode = QuestionStarterCodeDefaults.getDefaultStarterCode(
                QuestionSubmitLanguageEnum.JAVASCRIPT, config);

        assertTrue(goCode.contains("json.Unmarshal"));
        assertTrue(goCode.contains("_ = numsInput0"));
        assertTrue(goCode.contains("var answer []int = []int{}"));
        assertTrue(goCode.contains("json.Marshal(answer)"));
        assertTrue(pythonCode.contains("json.loads(sys.argv[1])"));
        assertTrue(pythonCode.contains("json.dumps(answer"));
        assertTrue(javascriptCode.contains("JSON.parse(process.argv[2])"));
        assertTrue(javascriptCode.contains("process.stdout.write(toJson(answer));"));
    }

    private JudgeCaseConfig config() {
        return configWithOutput("INTEGER_ARRAY");
    }

    private String javaOutputCode(String outputType) {
        return QuestionStarterCodeDefaults.getDefaultStarterCode(
                QuestionSubmitLanguageEnum.JAVA, configWithOutput(outputType));
    }

    private JudgeCaseConfig configWithOutput(String outputType) {
        JudgeCaseConfig config = new JudgeCaseConfig();
        config.setInputDefinitions(List.of(
                definition("nums", "INTEGER_ARRAY"),
                definition("target", "INTEGER")));
        JudgeValueDefinition outputDefinition = new JudgeValueDefinition();
        outputDefinition.setType(outputType);
        config.setOutputDefinition(outputDefinition);
        return config;
    }

    private JudgeParameterDefinition definition(String name, String type) {
        JudgeParameterDefinition definition = new JudgeParameterDefinition();
        definition.setName(name);
        definition.setType(type);
        return definition;
    }
}
