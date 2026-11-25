package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设置本题的提示档位 0-4（第 5 档通过看答案进入）
 */
@Data
public class CompanionLevelRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    private Integer level;
}
