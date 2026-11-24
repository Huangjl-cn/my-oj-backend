package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 前端编辑器当前的代码快照，每次请求都带上
 */
@Data
public class CodeSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "编辑器里的完整代码，原样传递（修改建议的 baseHash 按它计算）")
    @ToString.Exclude
    private String code;

    @Schema(description = "编程语言", example = "java")
    private String language;

    @Schema(description = "光标所在行，从 1 开始，可为空")
    private Integer cursorLine;

    @Schema(description = "选中区域起始行，从 1 开始，没有选中时为空")
    private Integer selectionStartLine;

    @Schema(description = "选中区域结束行（含），没有选中时为空")
    private Integer selectionEndLine;
}
