package com.hjl.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.question.QuestionQueryRequest;
import com.hjl.oj.model.dto.question.QuestionStarterCodeSaveRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.vo.QuestionStarterCodeVO;
import com.hjl.oj.model.vo.QuestionVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * @author hjl15
 * &#064;description  针对表【question(题目)】的数据库操作Service
 * &#064;createDate  2025-12-18 13:42:50
 */
public interface QuestionService extends IService<Question> {
    /**
     * 校验
     */
    void validQuestion(Question question, boolean add);

    /**
     * 创建题目及其初始代码模板。
     */
    long createQuestionWithStarterCodes(Question question, List<QuestionStarterCodeSaveRequest> starterCodeList);

    /**
     * 更新题目及其初始代码模板。
     */
    boolean updateQuestionWithStarterCodes(Question question, List<QuestionStarterCodeSaveRequest> starterCodeList);

    /**
     * 原子更新题目的提交数和通过数。
     *
     * @param questionId 题目 id
     * @param accepted   本次提交是否通过
     */
    boolean incrementJudgeCount(long questionId, boolean accepted);

    /**
     * 获取题目指定语言的初始代码模板。
     */
    QuestionStarterCodeVO getQuestionStarterCodeVO(long questionId, String language);

    /**
     * 获取题目全部初始代码模板。
     */
    List<QuestionStarterCodeVO> listQuestionStarterCodeVO(long questionId);

    /**
     * 获取查询条件
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    /**
     * 获取题目封装
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目封装
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);

}
