package com.hjl.oj.model.dto.companion;

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

    private Long questionId;
}
