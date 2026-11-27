package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个标签下的做题情况
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagProgressVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tag;

    @Schema(description = "题库里带这个标签的题目数")
    private Integer total;

    @Schema(description = "其中已通过的题目数")
    private Integer solved;

    @Schema(description = "其中做过但还没通过的题目数（含判题中）")
    private Integer attempted;
}
