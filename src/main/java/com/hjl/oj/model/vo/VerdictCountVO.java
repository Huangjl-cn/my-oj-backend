package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一种判题结果的提交次数
 */
@Data
@Schema(description = "一种判题结果的提交次数")
@NoArgsConstructor
@AllArgsConstructor
public class VerdictCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "accepted / wrong_answer / time_limit / memory_limit / runtime_error / compile_error / other")
    private String verdict;

    @Schema(description = "结果的中文名")
    private String text;

    private Integer count;
}
