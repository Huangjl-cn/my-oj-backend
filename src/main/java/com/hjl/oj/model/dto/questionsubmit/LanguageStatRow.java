package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某个用户用一种语言的提交汇总（服务端内部使用，不直接返回给前端）
 */
@Data
public class LanguageStatRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String language;

    private Long submitCount;

    private Long acceptedCount;

    /**
     * 用这种语言通过过的题目数
     */
    private Long solvedCount;
}
