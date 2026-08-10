package com.hjl.oj.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 前端可选的判题值类型。
 */
@Data
@AllArgsConstructor
public class SupportedJudgeTypeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String value;

    private String text;

    private String category;

    private int dimensions;

    private String elementType;
}
