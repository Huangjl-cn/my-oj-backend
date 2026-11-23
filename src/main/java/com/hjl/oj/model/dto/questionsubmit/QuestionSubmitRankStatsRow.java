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
     * 同题同语言、通过（Accepted）且有有效耗时的提交数（含本人若已通过）
     */
    private Long timeTotal;

    /**
     * 其中耗时大于等于目标提交的数量（持平计入）
     */
    private Long timeBeaten;

    /**
     * 同题同语言、通过（Accepted）且有有效内存的提交数（含本人若已通过）
     */
    private Long memoryTotal;

    /**
     * 其中内存大于等于目标提交的数量（持平计入）
     */
    private Long memoryBeaten;
}
