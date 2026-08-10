package com.hjl.oj.model.dto.question;

import lombok.Data;

/**
 * 题目单个输入参数的定义。
 */
@Data
public class JudgeParameterDefinition {

    /**
     * 参数名称，供代码模板中的变量语义使用。
     */
    private String name;

    /**
     * 参数类型，使用 JudgeValueTypeEnum 的 value。
     */
    private String type;
}
