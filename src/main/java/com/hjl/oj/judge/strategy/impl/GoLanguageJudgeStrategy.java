package com.hjl.oj.judge.strategy.impl;

import com.hjl.oj.judge.strategy.LanguageJudgeStrategy;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.stereotype.Component;

/**
 * Go 补偿运行时和垃圾回收器的基础内存占用。
 */
@Component
public class GoLanguageJudgeStrategy extends AbstractJudgeStrategy implements LanguageJudgeStrategy {

    private static final long ADDITIONAL_MEMORY_KB = 16 * 1024L;

    @Override
    public QuestionSubmitLanguageEnum getLanguage() {
        return QuestionSubmitLanguageEnum.GOLANG;
    }

    @Override
    protected long additionalMemoryLimit(JudgeContext judgeContext) {
        return ADDITIONAL_MEMORY_KB;
    }
}
