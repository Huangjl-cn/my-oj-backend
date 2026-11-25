package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按难度计数；没有难度标签的题只计入 all
 */
@Data
public class DifficultyCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "全部")
    private int all;

    private int easy;

    private int medium;

    private int hard;
}
