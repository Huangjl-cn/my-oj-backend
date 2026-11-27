package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题单卡片：标题、题目数、当前用户的进度
 */
@Data
public class QuestionListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String description;

    @Schema(description = "favorite 我的收藏 / custom 自建 / official 官方")
    private String type;

    @Schema(description = "题目数（已删除的题不算）")
    private Integer questionCount;

    @Schema(description = "其中当前用户已通过的题目数")
    private Integer solvedCount;

    @Schema(description = "当前用户能不能改这个题单（加题、删题、排序）")
    private Boolean editable;

    private Date updateTime;
}
