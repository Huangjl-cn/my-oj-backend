package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 手动整理记忆
 */
@Data
public class CompanionCompactRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "对话 id（会话接口返回的 conversationId）")
    private Long conversationId;
}
