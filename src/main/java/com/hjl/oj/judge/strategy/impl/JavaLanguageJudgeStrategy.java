package com.hjl.oj.judge.strategy.impl;

import com.hjl.oj.judge.strategy.LanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.stereotype.Component;

/**
 * Java 程序的判题策略
 */
@Component
public class JavaLanguageJudgeStrategy extends AbstractJudgeStrategy implements LanguageJudgeStrategy {

    private static final long ADDITIONAL_MEMORY_KB = 64 * 1024L;
    private static final long ADDITIONAL_TIME_MS = 2000L;

    @Override
    public QuestionSubmitLanguageEnum getLanguage() {
        return QuestionSubmitLanguageEnum.JAVA;
    }

    @Override
    protected long additionalTimeLimit(JudgeContext judgeContext) {
        return ADDITIONAL_TIME_MS;
    }

    @Override
    protected long additionalMemoryLimit(JudgeContext judgeContext) {
        return ADDITIONAL_MEMORY_KB;
    }
}
