package com.hjl.oj.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 判题用例支持的值类型。
 */
@Getter
public enum JudgeValueTypeEnum {

    INTEGER("整数", "INTEGER", "SCALAR", 0, null),
    LONG("长整数", "LONG", "SCALAR", 0, null),
    DOUBLE("小数", "DOUBLE", "SCALAR", 0, null),
    BOOLEAN("布尔值", "BOOLEAN", "SCALAR", 0, null),
    STRING("字符串", "STRING", "SCALAR", 0, null),
    INTEGER_ARRAY("整数数组", "INTEGER_ARRAY", "ARRAY", 1, "INTEGER"),
    INTEGER_MATRIX("二维整数数组", "INTEGER_MATRIX", "ARRAY", 2, "INTEGER"),
    STRING_ARRAY("字符串数组", "STRING_ARRAY", "ARRAY", 1, "STRING");

    private final String text;

    private final String value;

    private final String category;

    private final int dimensions;

    private final String elementType;

    JudgeValueTypeEnum(String text, String value, String category, int dimensions, String elementType) {
        this.text = text;
        this.value = value;
        this.category = category;
        this.dimensions = dimensions;
        this.elementType = elementType;
    }

    /**
     * 获取值列表。
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据稳定值获取枚举。
     */
    public static JudgeValueTypeEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (JudgeValueTypeEnum anEnum : values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
