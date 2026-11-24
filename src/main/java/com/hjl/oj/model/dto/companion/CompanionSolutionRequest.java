package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 看答案（解锁第 5 级）
 */
@Data
public class CompanionSolutionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    private CodeSnapshot snapshot;
}
