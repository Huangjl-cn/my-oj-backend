package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某个用户某一天的提交次数（服务端内部使用，不直接返回给前端）
 */
@Data
public class SubmitDayRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日期，yyyy-MM-dd（按数据库里的时间算）
     */
    private String day;

    private Long submitCount;

    private Long acceptedCount;
}
