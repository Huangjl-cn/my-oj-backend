package com.hjl.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.model.vo.QuestionSubmitSummaryVO;
import com.hjl.oj.model.vo.QuestionSubmitVO;

public interface QuestionSubmitService extends IService<QuestionSubmit> {

    /**
     * 题目提交
     */
    long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser);

    /**
     * 仅当提交处于预期状态时更新，保证状态转换由一条 SQL 原子完成。
     */
    boolean updateStatusIfCurrent(long questionSubmitId,
                                  QuestionSubmitStatusEnum currentStatus,
                                  QuestionSubmitStatusEnum targetStatus,
                                  String judgeInfo);

    /**
     * 获取查询条件
     */
    QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitQueryRequest);

    /**
     * 按提交 id 和用户 id 获取提交，避免当前用户访问其他用户记录。
     */
    QuestionSubmit getByIdAndUserId(long questionSubmitId, long userId);

    /**
     * 获取题目提交封装
     */
    QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser);

    /**
     * 分页获取题目提交封装
     */
    Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser);

    /**
     * 分页获取不含提交代码的摘要。
     */
    Page<QuestionSubmitSummaryVO> getQuestionSubmitSummaryVOPage(Page<QuestionSubmit> questionSubmitPage);
}
