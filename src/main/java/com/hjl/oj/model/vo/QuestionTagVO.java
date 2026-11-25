package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目标签和使用它的题目数（不含难度标签）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionTagVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tag;

    @Schema(description = "带这个标签的题目数")
    private Integer count;
}
