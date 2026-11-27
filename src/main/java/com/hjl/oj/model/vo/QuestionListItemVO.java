package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题单里的一道题
 */
@Data
public class QuestionListItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    private String title;

    @Schema(description = "题目标签（含难度标签）")
    private List<String> tags;

    private Integer submitNum;

    private Integer acceptedNum;

    @Schema(description = "加入题单的时间")
    private Date addTime;
}
