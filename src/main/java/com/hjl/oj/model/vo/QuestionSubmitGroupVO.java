package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 提交归档页按题目聚合封装类
 */
@Data
public class QuestionSubmitGroupVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目 id
     */
    @Schema(description = "题目 id")
    private Long questionId;

    /**
     * 题目标题
     */
    @Schema(description = "题目标题")
    private String questionTitle;

    /**
     * 题目标签列表
     */
    @Schema(description = "题目标签列表")
    private List<String> questionTags;

    /**
     * 筛选范围内最近一次提交的判题结果（accepted/failed/pending）
     */
    @Schema(description = "筛选范围内最近一次提交的判题结果，可选 accepted/failed/pending", example = "failed")
    private String latestResult;

    /**
     * 筛选范围内最近一次提交 id
     */
    @Schema(description = "筛选范围内最近一次提交 id")
    private Long latestSubmissionId;

    /**
     * 筛选范围内最近一次提交的编程语言
     */
    @Schema(description = "筛选范围内最近一次提交的编程语言")
    private String latestLanguage;

    /**
     * 筛选范围内最近一次提交的代码（对所有登录用户可见）
     */
    @Schema(description = "筛选范围内最近一次提交的代码")
    private String latestCode;

    /**
     * 筛选范围内最近一次提交时间
     */
    @Schema(description = "筛选范围内最近一次提交时间")
    private Date latestSubmitTime;

    /**
     * 筛选范围内最短判题耗时（ms），无有效指标时为 null
     */
    @Schema(description = "筛选范围内最短判题耗时（ms），无有效指标时为 null")
    private Long bestTime;

    /**
     * 筛选范围内最小判题内存（KB），无有效指标时为 null
     */
    @Schema(description = "筛选范围内最小判题内存（KB），无有效指标时为 null")
    private Long bestMemory;
}
