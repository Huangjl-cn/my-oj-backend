package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某个用户在一道题上的提交汇总（服务端内部使用，不直接返回给前端）
 */
@Data
public class QuestionProgressRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    /**
     * 是否有过通过的提交：1 / 0
     */
    private Integer accepted;

    /**
     * 是否有还在判题的提交：1 / 0
     */
    private Integer pending;
}
