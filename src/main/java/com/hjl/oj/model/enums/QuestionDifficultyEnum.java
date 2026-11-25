package com.hjl.oj.model.enums;

import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 题目难度：放在题目标签里，中英文写法都认（easy / 简单）
 */
@Getter
public enum QuestionDifficultyEnum {

    EASY("easy", "简单"),
    MEDIUM("medium", "中等"),
    HARD("hard", "困难");

    private final String value;

    private final String text;

    QuestionDifficultyEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 这个标签是不是难度
     */
    public static Optional<QuestionDifficultyEnum> fromTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        String normalized = tag.strip().toLowerCase(Locale.ROOT);
        for (QuestionDifficultyEnum difficulty : values()) {
            if (difficulty.value.equals(normalized) || difficulty.text.equals(normalized)) {
                return Optional.of(difficulty);
            }
        }
        return Optional.empty();
    }

    /**
     * 一组标签里的难度，取第一个能识别的
     */
    public static Optional<QuestionDifficultyEnum> fromTags(List<String> tags) {
        if (tags == null) {
            return Optional.empty();
        }
        return tags.stream().map(QuestionDifficultyEnum::fromTag).flatMap(Optional::stream).findFirst();
    }
}
