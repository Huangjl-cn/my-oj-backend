package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 请求分级提示
 */
@Data
public class CompanionHintRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    /**
     * 要到的等级 1-4，为空时在当前等级上加一
     */
    private Integer targetLevel;

    private CodeSnapshot snapshot;
}
