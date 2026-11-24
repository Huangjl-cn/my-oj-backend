package com.hjl.oj.model.enums;

import lombok.Getter;

/**
 * AI 陪练消息类型。
 */
@Getter
public enum CompanionMessageTypeEnum {

    CHAT("聊天", "chat"),
    HINT("提示", "hint"),
    REACTION("提交后反应", "reaction"),
    SOLUTION("参考解法", "solution"),
    COMPACTION("记忆整理", "compaction");

    private final String text;

    private final String value;

    CompanionMessageTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}
