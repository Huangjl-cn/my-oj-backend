package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个标签下通过的题平均第几次才过
 */
@Data
@Schema(description = "一个标签下通过的题平均第几次才过")
@NoArgsConstructor
@AllArgsConstructor
public class TagAttemptsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tag;

    @Schema(description = "这个标签下已通过的题目数")
    private Integer solved;

    @Schema(description = "平均第几次提交才通过，保留一位小数")
    private Double averageAttempts;
}
