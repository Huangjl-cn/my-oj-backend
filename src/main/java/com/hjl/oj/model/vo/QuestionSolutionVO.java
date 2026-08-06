package com.hjl.oj.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题解查询结果。
 */
@Data
public class QuestionSolutionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    private String answer;
}
