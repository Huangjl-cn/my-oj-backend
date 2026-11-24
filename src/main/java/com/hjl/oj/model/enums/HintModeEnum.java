package com.hjl.oj.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 用户的提示模式：循序渐进按等级透露，有问必答相当于一直处在最高等级。
 */
@Getter
public enum HintModeEnum {

    GUIDED("循序渐进", "guided"),
    DIRECT("有问必答", "direct");

    private final String text;

    private final String value;

    HintModeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static HintModeEnum getEnumByValue(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }
}
