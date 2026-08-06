package com.hjl.oj.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.hjl.oj.model.dto.question.QuestionStarterCodeSaveRequest;
import com.hjl.oj.model.entity.QuestionStarterCode;

import java.util.List;

/**
 * 题目初始代码模板服务
 */
public interface QuestionStarterCodeService extends IService<QuestionStarterCode> {

    /**
     * 校验并补全各语言模板。
     */
    List<QuestionStarterCode> normalizeStarterCodes(List<QuestionStarterCodeSaveRequest> starterCodeList);

    /**
     * 替换某道题的全部模板。
     */
    void replaceStarterCodes(long questionId, List<QuestionStarterCode> starterCodes);

    /**
     * 获取某道题指定语言的模板。
     */
    QuestionStarterCode getByQuestionIdAndLanguage(long questionId, String language);

    /**
     * 获取某道题全部模板。
     */
    List<QuestionStarterCode> listByQuestionId(long questionId);
}
