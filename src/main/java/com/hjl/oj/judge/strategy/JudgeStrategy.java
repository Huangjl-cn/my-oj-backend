package com.hjl.oj.judge.strategy;

import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.judge.strategy.model.JudgeContext;

/**
 * 判题策略
 */
public interface JudgeStrategy {

    /**
     * 根据沙箱结果和题目限制评估判题结果
     *
     * @param judgeContext 上下文
     * @return 判题信息
     */
    JudgeInfo evaluate(JudgeContext judgeContext);
}
