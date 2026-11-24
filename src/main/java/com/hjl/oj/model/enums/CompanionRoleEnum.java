package com.hjl.oj.model.enums;

import lombok.Getter;

/**
 * AI 陪练消息的发送方。
 */
@Getter
public enum CompanionRoleEnum {

    USER("用户", "user"),
    ASSISTANT("陪练", "assistant");

    private final String text;

    private final String value;

    CompanionRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}
