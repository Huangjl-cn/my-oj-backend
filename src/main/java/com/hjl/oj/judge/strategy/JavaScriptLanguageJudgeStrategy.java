package com.hjl.oj.judge.strategy;

import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.stereotype.Component;

/**
 * JavaScript 补偿 Node.js 运行时所需的时间和内存。
 */
@Component
public class JavaScriptLanguageJudgeStrategy extends AbstractJudgeStrategy implements LanguageJudgeStrategy {

    private static final long ADDITIONAL_MEMORY_KB = 32 * 1024L;
    private static final long ADDITIONAL_TIME_MS = 1000L;

    @Override
    public QuestionSubmitLanguageEnum getLanguage() {
        return QuestionSubmitLanguageEnum.JAVASCRIPT;
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
