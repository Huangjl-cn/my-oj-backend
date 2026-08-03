package com.hjl.oj.judge.strategy;

import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;

/**
 * 声明某种语言的专用判题策略。
 */
public interface LanguageJudgeStrategy extends JudgeStrategy {

    QuestionSubmitLanguageEnum getLanguage();
}
