package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 切换到之前的某段对话
 */
@Data
public class CompanionSwitchConversationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long conversationId;
}
