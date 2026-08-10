package com.hjl.oj.model.dto.question;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * 题目用例
 */
@Data
public class JudgeCase {
    /**
     * 输入参数，顺序与题目的 inputDefinitions 一致。
     */
    private List<JsonNode> inputs;

    /**
     * 预期输出。
     */
    private JsonNode expectedOutput;
}
