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
}
