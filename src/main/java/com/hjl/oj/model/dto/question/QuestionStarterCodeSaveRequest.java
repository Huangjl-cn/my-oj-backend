package com.hjl.oj.model.dto.question;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目初始代码模板保存项
 */
@Data
public class QuestionStarterCodeSaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 编辑器初始代码模板，为空时使用该语言默认模板
     */
    private String starterCode;
}
