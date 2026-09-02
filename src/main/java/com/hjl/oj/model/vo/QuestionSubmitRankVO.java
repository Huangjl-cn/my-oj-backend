package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交指标击败率封装类（全站同语言对比）
 */
@Data
public class QuestionSubmitRankVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提交 id
     */
    @Schema(description = "提交 id")
    private Long submissionId;

    /**
     * 题目 id（对比人群限定在该题目内）
     */
    @Schema(description = "题目 id，对比人群限定在该题目内")
    private Long questionId;

    /**
     * 编程语言
     */
    @Schema(description = "编程语言")
    private String language;

    /**
     * 判题耗时（ms），无有效指标时为 null
     */
    @Schema(description = "判题耗时（ms），无有效指标时为 null")
    private Long time;

    /**
     * 判题内存（KB），无有效指标时为 null
     */
    @Schema(description = "判题内存（KB），无有效指标时为 null")
    private Long memory;

    /**
     * 同题同语言、通过（Accepted）且有有效耗时的提交总数（含本人若已通过）
     */
    @Schema(description = "同题同语言、通过（Accepted）且有有效耗时的提交总数（含本人若已通过）")
    private Long timeTotal;

    /**
     * 其中耗时大于等于本次提交的数量（持平计入超过）
     */
    @Schema(description = "其中耗时大于等于本次提交的数量（持平计入超过），首次通过提交时为 1/1 即超过 100%")
    private Long timeBeaten;

    /**
     * 同题同语言、通过（Accepted）且有有效内存的提交总数（含本人若已通过）
     */
    @Schema(description = "同题同语言、通过（Accepted）且有有效内存的提交总数（含本人若已通过）")
    private Long memoryTotal;

    /**
     * 其中内存大于等于本次提交的数量（持平计入超过）
     */
    @Schema(description = "其中内存大于等于本次提交的数量（持平计入超过），首次通过提交时为 1/1 即超过 100%")
    private Long memoryBeaten;
}
