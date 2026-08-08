package com.hjl.oj.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.constant.CommonConstant;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.JudgeService;
import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.model.vo.QuestionSubmitSummaryVO;
import com.hjl.oj.model.vo.QuestionSubmitVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.UserService;
import com.hjl.oj.utils.SqlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuestionSubmitServiceImpl extends ServiceImpl<QuestionSubmitMapper, QuestionSubmit>
        implements QuestionSubmitService {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private JudgeService judgeService;

    @Resource(name = "judgeExecutor")
    private ExecutorService judgeExecutor;

    /**
     * 提交题目
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser) {
        //检验编程语言是否合法
        String language = questionSubmitAddRequest.getLanguage();
        QuestionSubmitLanguageEnum languageEnum = QuestionSubmitLanguageEnum.getEnumByValue(language);
        if (languageEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言错误");
        }
        // 判断实体是否存在，根据类别获取实体
        Question question = questionService.getById(questionSubmitAddRequest.getQuestionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已提交题目
        long userId = loginUser.getId();
        // 每个用户串行提交题目
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setUserId(userId);
        questionSubmit.setQuestionId(questionSubmitAddRequest.getQuestionId());
        questionSubmit.setCode(questionSubmitAddRequest.getCode());
        questionSubmit.setLanguage(language);
        //初始化题目提交状态
        questionSubmit.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
        questionSubmit.setJudgeInfo("{}");
        boolean save = this.save(questionSubmit);
        if (!save) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据插入失败");
        }
        // 事务提交后再执行判题，避免异步线程读取不到刚保存的提交记录。
        Long questionSubmitId = questionSubmit.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scheduleJudge(questionSubmitId);
            }
        });
        return questionSubmitId;
    }

    @Override
    public boolean updateStatusIfCurrent(long questionSubmitId,
                                         QuestionSubmitStatusEnum currentStatus,
                                         QuestionSubmitStatusEnum targetStatus,
                                         String judgeInfo) {
        LambdaUpdateWrapper<QuestionSubmit> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionSubmit::getId, questionSubmitId)
                .eq(QuestionSubmit::getStatus, currentStatus.getValue())
                .eq(QuestionSubmit::getIsDelete, 0)
                .set(QuestionSubmit::getStatus, targetStatus.getValue());
        if (judgeInfo != null) {
            updateWrapper.set(QuestionSubmit::getJudgeInfo, judgeInfo);
        }
        return this.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionSubmit completeSubmissionAndUpdateStats(long questionSubmitId,
                                                           long questionId,
                                                           String judgeInfo,
                                                           boolean accepted) {
        boolean completed = updateStatusIfCurrent(
                questionSubmitId,
                QuestionSubmitStatusEnum.RUNNING,
                QuestionSubmitStatusEnum.SUCCEED,
                judgeInfo);
        if (!completed) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        boolean counted = questionService.incrementJudgeCount(questionId, accepted);
        if (!counted) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目统计更新错误");
        }
        return getById(questionSubmitId);
    }

    private void scheduleJudge(long questionSubmitId) {
        // 使用专用虚拟线程异步判题，异常由判题服务同步到提交状态。
        judgeExecutor.execute(() -> {
            try {
                judgeService.processSubmission(questionSubmitId);
            } catch (Exception e) {
                log.error("判题任务执行失败，questionSubmitId={}", questionSubmitId, e);
            }
        });
    }

    /**
     * 获取查询包装类：前端根据用户可能会用到哪些字段查询，传递一个请求对象，返回mybatis框架支持的查询QueryWrapper类
     */
    @Override
    public QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitSubmitQueryRequest) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        if (questionSubmitSubmitQueryRequest == null) {
            return queryWrapper;
        }
        //考虑用户会用哪些字段来查询
        Long questionId = questionSubmitSubmitQueryRequest.getQuestionId();
        Long userId = questionSubmitSubmitQueryRequest.getUserId();
        String language = questionSubmitSubmitQueryRequest.getLanguage();
        Integer status = questionSubmitSubmitQueryRequest.getStatus();
        String sortField = questionSubmitSubmitQueryRequest.getSortField();
        String sortOrder = questionSubmitSubmitQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(StringUtils.isNotEmpty(language), "language", language);
        queryWrapper.eq(QuestionSubmitStatusEnum.getEnumByValue(status) != null, "status", status);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionSubmit getByIdAndUserId(long questionSubmitId, long userId) {
        return this.lambdaQuery()
                .eq(QuestionSubmit::getId, questionSubmitId)
                .eq(QuestionSubmit::getUserId, userId)
                .one();
    }

    /**
     * 用于获取实体的封装类
     */
    @Override
    public QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser) {
        QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
        Long userId = loginUser.getId();
        //脱敏处理：除了当前用户和管理员，其余用户是不允许查看提交代码的
        if (!userId.equals(questionSubmit.getUserId()) && !userService.isAdmin(loginUser)) {
            questionSubmitVO.setCode(null);
        }
        return questionSubmitVO;
    }

    /**
     * 分页获取实体的封装类
     *
     * @param questionSubmitPage 分页实体
     * @param loginUser          当前用户
     * @return 分页封装类
     */
    @Override
    public Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<QuestionSubmitVO> questionSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(), questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        if (CollUtil.isEmpty(questionSubmitList)) {
            return questionSubmitVOPage;
        }
        //暂时只做了脱敏，没有关联用户和题目
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitList.stream()
                .map(questionSubmit -> getQuestionSubmitVO(questionSubmit, loginUser))
                .collect(Collectors.toList());
        questionSubmitVOPage.setRecords(questionSubmitVOList);
        return questionSubmitVOPage;
    }

    @Override
    public Page<QuestionSubmitSummaryVO> getQuestionSubmitSummaryVOPage(Page<QuestionSubmit> questionSubmitPage) {
        Page<QuestionSubmitSummaryVO> summaryPage = new Page<>(questionSubmitPage.getCurrent(),
                questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        List<QuestionSubmitSummaryVO> summaryList = questionSubmitPage.getRecords().stream()
                .map(QuestionSubmitSummaryVO::objToVo)
                .collect(Collectors.toList());
        summaryPage.setRecords(summaryList);
        return summaryPage;
    }
}




