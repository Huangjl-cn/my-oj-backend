package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 删除一段对话
 */
@Data
public class CompanionDeleteConversationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "要删除的对话 id")
    private Long conversationId;
}
