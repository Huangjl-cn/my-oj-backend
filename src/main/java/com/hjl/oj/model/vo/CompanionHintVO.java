package com.hjl.oj.model.vo;

import com.hjl.oj.companion.edit.CodeEditProposal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条分级提示
 */
@Data
public class CompanionHintVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;

    @Schema(description = "这条提示按哪一档给的")
    private Integer hintLevel;

    @Schema(description = "这一档的名字，如「点关键」")
    private String stepTitle;

    private String message;

    @Schema(description = "代码修改建议，没有为 null")
    private CodeEditProposal codeEdits;

    private ContextUsageVO contextUsage;
}
