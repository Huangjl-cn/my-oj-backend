package com.hjl.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某个用户判完的提交按「状态 + 判题消息第一行」分组的次数（服务端内部使用，再归成几类结果）
 */
@Data
public class VerdictCountRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer status;

    /**
     * judgeInfo.message 的第一行
     */
    private String message;

    private Long submitCount;
}
