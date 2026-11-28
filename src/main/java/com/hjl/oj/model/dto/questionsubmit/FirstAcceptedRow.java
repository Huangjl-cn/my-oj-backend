package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 某个用户在一道题上第一次通过的那次提交（服务端内部使用，不直接返回给前端）
 */
@Data
public class FirstAcceptedRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    /**
     * 第一次通过的时间
     */
    private Date solvedTime;

    /**
     * 第一次通过用的语言
     */
    private String language;

    /**
     * 这是在这道题上的第几次提交（含这次）
     */
    private Long attempts;
}
