package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 当前用户的学习统计（按难度的进度和做题日历另有接口，这里不重复）
 */
@Data
public class LearningStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "各标签的做题情况，难度标签不算；按题目数从多到少")
    private List<TagProgressVO> tags;

    @Schema(description = "各语言的提交情况，按提交次数从多到少")
    private List<LanguageStatVO> languages;

    @Schema(description = "最近第一次通过的题，最近的在前，最多 10 道")
    private List<SolvedQuestionVO> recentSolved;

    @Schema(description = "第一次提交就通过的题目数")
    private Integer firstTrySolved;

    @Schema(description = "最近一次就通过的题，最近的在前，最多 6 道")
    private List<SolvedQuestionVO> firstTryRecent;

    @Schema(description = "最近一年一天里各个小时（0-23）的提交次数，24 个数")
    private List<Integer> hourCounts;

    @Schema(description = "判完的提交按结果分的次数，次数从多到少，没有的结果不出现")
    private List<VerdictCountVO> verdicts;

    @Schema(description = "最常卡住的标签：已通过的题平均第几次才过，从多到少，最多 3 个")
    private List<TagAttemptsVO> stuckTags;
}
