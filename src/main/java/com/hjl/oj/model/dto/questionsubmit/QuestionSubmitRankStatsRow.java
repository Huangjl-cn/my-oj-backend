package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 同语言判题指标排名统计行（服务端内部使用，不直接返回给前端）
 */
@Data
public class QuestionSubmitRankStatsRow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 同语言、判题成功且有有效耗时的其他提交数（不含本人）
     */
    private Long timeTotal;

    /**
     * 其中耗时比目标提交更大（更差）的数量
     */
    private Long timeBeaten;

    /**
     * 同语言、判题成功且有有效内存的其他提交数（不含本人）
     */
    private Long memoryTotal;

    /**
     * 其中内存比目标提交更大（更差）的数量
     */
    private Long memoryBeaten;
}
