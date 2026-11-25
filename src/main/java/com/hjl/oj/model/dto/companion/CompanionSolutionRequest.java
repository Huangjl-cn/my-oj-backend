package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 看答案（档位变为 5）
 */
@Data
public class CompanionSolutionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "对话 id（会话接口返回的 conversationId）")
    private Long conversationId;

    private CodeSnapshot snapshot;
}
