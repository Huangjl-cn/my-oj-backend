package com.hjl.oj.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

/**
 * 题目提交语言业务类型枚举
 */
@Getter
public enum QuestionSubmitLanguageEnum {

    JAVA("Java 25", "java"),
    CPLUSPLUS("C++ 17 (GCC 15)", "cpp"),
    GOLANG("Go 1.25", "go"),
    PYTHON("Python 3.14", "python"),
    JAVASCRIPT("JavaScript (Node.js 24)", "javascript");

    private final String text;

    private final String value;

    QuestionSubmitLanguageEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static QuestionSubmitLanguageEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (QuestionSubmitLanguageEnum anEnum : QuestionSubmitLanguageEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

}
