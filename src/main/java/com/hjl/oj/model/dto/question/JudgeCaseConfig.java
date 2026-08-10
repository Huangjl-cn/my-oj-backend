package com.hjl.oj.model.dto.question;

import lombok.Data;

import java.util.List;

/**
 * 一道题完整的结构化判题配置。
 */
@Data
public class JudgeCaseConfig {

    /**
     * 输入参数定义。
     */
    private List<JudgeParameterDefinition> inputDefinitions;

    /**
     * 输出定义。
     */
    private JudgeValueDefinition outputDefinition;

    /**
     * 测试用例列表。
     */
    private List<JudgeCase> cases;
}
