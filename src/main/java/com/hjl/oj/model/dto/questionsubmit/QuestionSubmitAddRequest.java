package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增题目提交
 */
@Data
public class QuestionSubmitAddRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 题目 id
     */
    private Long questionId;
    /**
     * 编程语言
     */
    private String language;
    /**
     * 用户提交代码
     */
    private String code;
}