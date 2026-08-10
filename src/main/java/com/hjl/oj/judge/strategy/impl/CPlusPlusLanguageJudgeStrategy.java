package com.hjl.oj.judge.strategy.impl;

import com.hjl.oj.judge.strategy.LanguageJudgeStrategy;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.stereotype.Component;

/**
 * C++ 使用题目配置的基准资源限制。
 */
@Component
public class CPlusPlusLanguageJudgeStrategy extends AbstractJudgeStrategy implements LanguageJudgeStrategy {

    @Override
    public QuestionSubmitLanguageEnum getLanguage() {
        return QuestionSubmitLanguageEnum.CPLUSPLUS;
    }
}
