package com.hjl.oj.model.dto.question;

import lombok.Data;

/**
 * 题目输出值定义。
 */
@Data
public class JudgeValueDefinition {

    /**
     * 输出类型，使用 JudgeValueTypeEnum 的 value。
     */
    private String type;
}
