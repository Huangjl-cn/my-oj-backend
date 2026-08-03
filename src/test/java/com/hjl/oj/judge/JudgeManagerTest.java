package com.hjl.oj.judge;

import com.hjl.oj.judge.strategy.DefaultJudgeStrategy;
import com.hjl.oj.judge.strategy.CPlusPlusLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.GoLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.JavaLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.JavaScriptLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.PythonLanguageJudgeStrategy;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JudgeManagerTest {

    @Test
    void everySupportedLanguageResolvesToAStrategy() {
        DefaultJudgeStrategy defaultStrategy = new DefaultJudgeStrategy();
        JavaLanguageJudgeStrategy javaStrategy = new JavaLanguageJudgeStrategy();
        CPlusPlusLanguageJudgeStrategy cppStrategy = new CPlusPlusLanguageJudgeStrategy();
        GoLanguageJudgeStrategy goStrategy = new GoLanguageJudgeStrategy();
        PythonLanguageJudgeStrategy pythonStrategy = new PythonLanguageJudgeStrategy();
        JavaScriptLanguageJudgeStrategy javaScriptStrategy = new JavaScriptLanguageJudgeStrategy();
        JudgeManager judgeManager = new JudgeManager(defaultStrategy,
                List.of(javaStrategy, cppStrategy, goStrategy, pythonStrategy, javaScriptStrategy));

        for (QuestionSubmitLanguageEnum language : QuestionSubmitLanguageEnum.values()) {
            assertNotNull(judgeManager.resolveStrategy(language));
        }
        assertSame(javaStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.JAVA));
        assertSame(cppStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.CPLUSPLUS));
        assertSame(goStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.GOLANG));
        assertSame(pythonStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.PYTHON));
        assertSame(javaScriptStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.JAVASCRIPT));
    }
}
