package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 按题目聚合分页查询的行对象（服务端内部使用，不直接返回给前端）
 */
@Data
public class QuestionSubmitGroupRow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 题目标题
     */
    private String questionTitle;

    /**
     * 题目标签（json 数组字符串）
     */
    private String questionTags;

    /**
     * 筛选范围内最近一次提交 id
     */
    private Long latestSubmissionId;

    /**
     * 筛选范围内最近一次提交的编程语言
     */
    private String latestLanguage;

    /**
     * 筛选范围内最近一次提交的代码
     */
    private String latestCode;

    /**
     * 筛选范围内最近一次提交的判题状态
     */
    private Integer latestStatus;

    /**
     * 筛选范围内最近一次提交的判题信息（json 对象字符串）
     */
    private String latestJudgeInfo;

    /**
     * 筛选范围内最近一次提交时间
     */
    private Date latestSubmitTime;

    /**
     * 筛选范围内最短判题耗时（ms），无有效指标时为 null
     */
    private Long bestTime;

    /**
     * 筛选范围内最小判题内存（KB），无有效指标时为 null
     */
    private Long bestMemory;
}
