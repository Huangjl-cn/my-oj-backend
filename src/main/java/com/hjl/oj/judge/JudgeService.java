package com.hjl.oj.judge;

import com.hjl.oj.model.entity.QuestionSubmit;

/**
 * 判题服务
 */
public interface JudgeService {

    /**
     * 处理提交的完整判题生命周期
     *
     * @param questionSubmitId 题目提交 id
     * @return 更新后的题目提交数据
     */
    QuestionSubmit processSubmission(long questionSubmitId);
}
