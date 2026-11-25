package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户在编辑器里选中、拿来提问的一段代码
 */
@Data
public class CodeQuote implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "起始行，从 1 开始")
    private Integer startLine;

    @Schema(description = "结束行（含）")
    private Integer endLine;

    @Schema(description = "选中的代码原文")
    @ToString.Exclude
    private String code;
}
