package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某个用户在一天里某个小时的提交次数（服务端内部使用）
 */
@Data
public class HourCountRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 0-23
     */
    private Integer hour;

    private Long submitCount;
}
