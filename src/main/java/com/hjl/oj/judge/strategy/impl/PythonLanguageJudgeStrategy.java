package com.hjl.oj.judge.strategy.impl;

import com.hjl.oj.judge.strategy.LanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.stereotype.Component;

/**
 * Python 补偿解释器启动所需的时间和内存。
 */
@Component
public class PythonLanguageJudgeStrategy extends AbstractJudgeStrategy implements LanguageJudgeStrategy {

    private static final long ADDITIONAL_MEMORY_KB = 32 * 1024L;
    private static final long ADDITIONAL_TIME_MS = 2000L;

    @Override
    public QuestionSubmitLanguageEnum getLanguage() {
        return QuestionSubmitLanguageEnum.PYTHON;
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
