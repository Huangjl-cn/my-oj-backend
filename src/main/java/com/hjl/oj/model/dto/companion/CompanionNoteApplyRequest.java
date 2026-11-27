package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用鸭鸭的一条笔记提议
 */
@Data
public class CompanionNoteApplyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "提议所在的消息 id")
    private Long messageId;

    @Schema(description = "第几条提议（消息的 noteEdits 下标，从 0 开始）")
    private Integer index;

    @Schema(description = "笔记在提议之后改过、而提议要整篇改写时，确认覆盖传 true")
    private Boolean overwrite;
}
