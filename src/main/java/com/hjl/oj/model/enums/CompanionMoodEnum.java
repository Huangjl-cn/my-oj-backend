package com.hjl.oj.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * AI 陪练的心情（调色板）。由模型按情境挑选，前端为每种心情做一组动画。
 */
@Getter
public enum CompanionMoodEnum {

    EXCITED("兴奋", "excited"),
    PROUD("骄傲", "proud"),
    SURPRISED("惊喜", "surprised"),
    THOUGHTFUL("若有所思", "thoughtful"),
    SYMPATHETIC("心疼", "sympathetic"),
    ENCOURAGING("打气", "encouraging"),
    PLAYFUL("调皮", "playful"),
    NERVOUS("紧张", "nervous"),
    CONFUSED("困惑", "confused");

    private final String text;

    private final String value;

    CompanionMoodEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static CompanionMoodEnum getEnumByValue(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }
}
