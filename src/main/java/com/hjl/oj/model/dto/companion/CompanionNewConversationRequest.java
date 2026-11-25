package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在某道题下开一段新对话
 */
@Data
public class CompanionNewConversationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;
}
