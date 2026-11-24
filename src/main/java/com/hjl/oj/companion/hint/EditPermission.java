package com.hjl.oj.companion.hint;

import lombok.Getter;

/**
 * 陪练修改用户代码的权限，随提示等级放开，由 CodeEditPolicy 强制执行。
 */
@Getter
public enum EditPermission {

    SYNTAX_ONLY("只能修语法、编译错误这类很小的改动：每处只改一行且改动很少，或补一行缺失的 import / include"),
    ANNOTATE("在只修语法的基础上，可以插入注释形式的批注，标出值得注意的地方（不改代码逻辑）"),
    SMALL_CHANGE("在可以批注的基础上，每处最多改动 3 行有效代码"),
    UNRESTRICTED("不限制，可以直接改好或整体替换");

    /**
     * 写进提示词，让模型知道自己能改到什么程度
     */
    private final String description;

    EditPermission(String description) {
        this.description = description;
    }
}
