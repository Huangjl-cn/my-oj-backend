package com.hjl.oj.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 前端可选的编程语言
 */
@Data
@AllArgsConstructor
public class SupportedLanguageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 前端展示名称，包含当前运行环境版本
     */
    private String text;

    /**
     * 提交和查询使用的稳定语言标识
     */
    private String value;
}
