package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可选模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModelOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模型 id", example = "deepseek-flash")
    private String id;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "上下文窗口（token 数）")
    private Integer contextWindow;
}
