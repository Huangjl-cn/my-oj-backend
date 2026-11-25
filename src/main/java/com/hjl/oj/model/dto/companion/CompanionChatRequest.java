package com.hjl.oj.model.dto.companion;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "对话 id（会话接口返回的 conversationId）")
    private Long conversationId;

    /**
     * 用户说的话（对话内容不进请求日志）
     */
    @ToString.Exclude
    private String message;

    @Schema(description = "选中代码后提问时引用的那一段，可为空")
    private CodeQuote quote;

    private CodeSnapshot snapshot;
}
