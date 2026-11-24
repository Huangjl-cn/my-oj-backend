package com.hjl.oj.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 分级提示的方向：没思路时找思路，思路对但代码有 bug 时找 bug。
 */
@Getter
public enum HintTrackEnum {

    IDEA("找思路", "idea"),
    BUG("找 bug", "bug");

    private final String text;

    private final String value;

    HintTrackEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static HintTrackEnum getEnumByValue(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(IDEA);
    }
}
