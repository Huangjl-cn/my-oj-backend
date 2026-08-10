package com.hjl.oj.judge;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.judge.strategy.comparator.JudgeOutputComparator;
import com.hjl.oj.judge.strategy.impl.CPlusPlusLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.impl.DefaultJudgeStrategy;
import com.hjl.oj.judge.strategy.impl.GoLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.impl.JavaLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.impl.JavaScriptLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.impl.PythonLanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.dto.question.JudgeConfig;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.ExecuteStatusEnum;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                List.of(javaStrategy, cppStrategy, goStrategy, pythonStrategy, javaScriptStrategy),
                mock(JudgeOutputComparator.class));

        for (QuestionSubmitLanguageEnum language : QuestionSubmitLanguageEnum.values()) {
            assertNotNull(judgeManager.resolveStrategy(language));
        }
        assertSame(javaStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.JAVA));
        assertSame(cppStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.CPLUSPLUS));
        assertSame(goStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.GOLANG));
        assertSame(pythonStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.PYTHON));
        assertSame(javaScriptStrategy, judgeManager.resolveStrategy(QuestionSubmitLanguageEnum.JAVASCRIPT));
    }

    @Test
    void successfulResourceCheckDelegatesToSharedOutputComparator() {
        DefaultJudgeStrategy defaultStrategy = new DefaultJudgeStrategy();
        JudgeOutputComparator outputComparator = mock(JudgeOutputComparator.class);
        JudgeManager judgeManager = new JudgeManager(defaultStrategy, List.of(), outputComparator);
        JudgeContext context = acceptedContext();
        JudgeInfo comparedResult = new JudgeInfo();
        comparedResult.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        when(outputComparator.compare(eq(context), any(JudgeInfo.class))).thenReturn(comparedResult);

        JudgeInfo result = judgeManager.applyStrategy(context);

        assertSame(comparedResult, result);
        verify(outputComparator).compare(eq(context), any(JudgeInfo.class));
    }

    private JudgeContext acceptedContext() {
        JudgeConfig judgeConfig = new JudgeConfig();
        judgeConfig.setMemoryLimit(1024L);
        judgeConfig.setTimeLimit(1000L);
        Question question = new Question();
        question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));

        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setLanguage(QuestionSubmitLanguageEnum.CPLUSPLUS.getValue());

        JudgeInfo executeInfo = new JudgeInfo();
        executeInfo.setMemory(1L);
        executeInfo.setTime(1L);

        JudgeContext context = new JudgeContext();
        context.setExecuteStatus(ExecuteStatusEnum.ACCEPTED.getValue());
        context.setJudgeInfo(executeInfo);
        context.setQuestion(question);
        context.setQuestionSubmit(questionSubmit);
        return context;
    }
}
