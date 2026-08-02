package com.hjl.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.question.QuestionQueryRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.vo.QuestionVO;
import jakarta.servlet.http.HttpServletRequest;

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
