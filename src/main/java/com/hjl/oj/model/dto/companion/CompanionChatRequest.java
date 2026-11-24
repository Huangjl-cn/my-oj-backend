package com.hjl.oj.model.dto.companion;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 和陪练聊天
 */
@Data
public class CompanionChatRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    /**
     * 用户说的话（对话内容不进请求日志）
     */
    @ToString.Exclude
    private String message;

    private CodeSnapshot snapshot;
}
