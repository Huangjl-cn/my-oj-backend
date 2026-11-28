package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 一道已通过的题，以及第一次通过时的情况
 */
@Data
@Schema(description = "一道通过了的题：第一次通过的时间、语言、第几次提交")
@NoArgsConstructor
@AllArgsConstructor
public class SolvedQuestionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    private String title;

    @Schema(description = "题目标签（含难度标签）")
    private List<String> tags;

    @Schema(description = "第一次通过的时间")
    private Date solvedTime;

    @Schema(description = "第一次通过用的语言的显示名")
    private String languageName;

    @Schema(description = "第几次提交才通过（含通过的那次），1 就是一次通过")
    private Integer attempts;
}
