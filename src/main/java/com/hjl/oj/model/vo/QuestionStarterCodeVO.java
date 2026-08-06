package com.hjl.oj.model.vo;

import com.hjl.oj.model.entity.QuestionStarterCode;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;

/**
 * 题目初始代码模板视图
 */
@Data
public class QuestionStarterCodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 编辑器初始代码模板
     */
    private String starterCode;

    public static QuestionStarterCodeVO objToVo(QuestionStarterCode questionStarterCode) {
        if (questionStarterCode == null) {
            return null;
        }
        QuestionStarterCodeVO questionStarterCodeVO = new QuestionStarterCodeVO();
        BeanUtils.copyProperties(questionStarterCode, questionStarterCodeVO);
        return questionStarterCodeVO;
    }
}
