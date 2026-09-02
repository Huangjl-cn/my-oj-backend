package com.hjl.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitArchiveQueryRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitGroupQueryRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.model.vo.QuestionSubmitGroupVO;
import com.hjl.oj.model.vo.QuestionSubmitRankVO;
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
     * 原子完成判题并更新题目统计。
     */
    QuestionSubmit completeSubmissionAndUpdateStats(long questionSubmitId,
                                                    long questionId,
                                                    String judgeInfo,
                                                    boolean accepted);

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

    /**
     * 分页获取提交归档列表（全部提交视图）：按判题结果筛选与多字段排序，提交代码对所有登录用户可见，
     * 补充题目与用户摘要及派生判题结果。
     */
    Page<QuestionSubmitVO> getQuestionSubmitArchiveVOPage(QuestionSubmitArchiveQueryRequest archiveQueryRequest,
                                                          User loginUser);

    /**
     * 分页获取按题目聚合的提交归档（按题目视图）：先筛选提交再按题目聚合，total 为题目数量。
     */
    Page<QuestionSubmitGroupVO> getQuestionSubmitGroupVOPage(QuestionSubmitGroupQueryRequest groupQueryRequest,
                                                             User loginUser);

    /**
     * 获取提交的指标排名数据：对比人群为同题同语言、通过（Accepted）且指标有效（含本人若已通过）的提交，
     * 返回人群总数与指标大于等于本次提交（含本人与持平）的数量，比例由前端计算展示。
     */
    QuestionSubmitRankVO getQuestionSubmitRank(long submissionId);
}
