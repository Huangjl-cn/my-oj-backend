package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提示阶梯上的一级（给用户看的名字和说明）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HintStepVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "档位 0-5")
    private Integer level;

    @Schema(description = "名字，如「点关键」")
    private String title;

    @Schema(description = "一句说明这一档会说到什么程度")
    private String description;
}
