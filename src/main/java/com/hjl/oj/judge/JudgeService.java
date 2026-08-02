package com.hjl.oj.judge;

import com.hjl.oj.model.entity.QuestionSubmit;

/**
 * 判题服务
 */
public interface JudgeService {

    /**
     * 判题
     *
     * @param questionSubmitId 题目提交 id
     * @return 更新后的题目提交数据
     */
    QuestionSubmit doJudge(long questionSubmitId);
}
