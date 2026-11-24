package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 记忆整理结果
 */
@Data
public class CompanionCompactionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否真的整理了（对话太短时不整理）")
    private Boolean compacted;

    private Long messageId;

    @Schema(description = "整理出的笔记全文")
    private String note;

    @Schema(description = "被整理掉的轮数")
    private Integer compactedRounds;

    @Schema(description = "原样保留的最近轮数")
    private Integer keptRounds;

    private ContextUsageVO contextUsage;
}
