package com.hjl.oj.controller;

import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.model.vo.SupportedJudgeTypeVO;
import com.hjl.oj.model.vo.SupportedLanguageVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionControllerSupportedLanguageTest {

    @Test
    void supportedLanguagesExposeStableValuesAndRuntimeLabels() {
        BaseResponse<List<SupportedLanguageVO>> response = new QuestionController().getSupportedLanguages();

        assertEquals(List.of("java", "cpp", "go", "python", "javascript"),
                response.getData().stream().map(SupportedLanguageVO::getValue).toList());
        assertEquals(List.of("Java 25", "C++ 17 (GCC 15)", "Go 1.25", "Python 3.14",
                        "JavaScript (Node.js 24)"),
                response.getData().stream().map(SupportedLanguageVO::getText).toList());
    }

    @Test
    void supportedJudgeTypesExposeRenderingMetadata() {
        BaseResponse<List<SupportedJudgeTypeVO>> response = new QuestionController().getSupportedJudgeTypes();

        assertEquals(List.of("INTEGER", "LONG", "DOUBLE", "BOOLEAN", "STRING",
                        "INTEGER_ARRAY", "INTEGER_MATRIX", "STRING_ARRAY"),
                response.getData().stream().map(SupportedJudgeTypeVO::getValue).toList());
        assertEquals(List.of(0, 0, 0, 0, 0, 1, 2, 1),
                response.getData().stream().map(SupportedJudgeTypeVO::getDimensions).toList());
    }
}
