package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上下文占用，前端显示成「3.2% · 32K / 1M」
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextUsageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "当前上下文 token 数")
    private Integer usedTokens;

    @Schema(description = "模型上下文窗口 token 数")
    private Integer limitTokens;

    @Schema(description = "占用比例 0-1")
    private Double ratio;

    @Schema(description = "达到这个比例时自动整理记忆")
    private Double compactRatio;

    @Schema(description = "是否为估算值（刚压缩完、还没有调用过模型时）")
    private Boolean estimated;
}
