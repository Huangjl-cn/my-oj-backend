package com.hjl.oj.model.enums;

import lombok.Getter;

/**
 * 题单类型
 */
@Getter
public enum QuestionListTypeEnum {

    /**
     * 我的收藏：每人一个，第一次用到时自动建，不能改名、不能删
     */
    FAVORITE("favorite"),

    /**
     * 自己建的题单，只有自己看得到
     */
    CUSTOM("custom"),

    /**
     * 官方题单：管理员建、管理员改，所有登录用户都看得到
     */
    OFFICIAL("official");

    private final String value;

    QuestionListTypeEnum(String value) {
        this.value = value;
    }
}
