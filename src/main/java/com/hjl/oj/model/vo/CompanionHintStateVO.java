package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某道题的提示状态
 */
@Data
public class CompanionHintStateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    @Schema(description = "提示档位 0-5")
    private Integer hintLevel;

    @Schema(description = "实际生效的档位")
    private Integer effectiveLevel;

}
