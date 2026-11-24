package com.hjl.oj.companion.edit;

/**
 * 一处代码修改建议。
 *
 * @param kind      replace 局部替换 / rewrite 整体替换
 * @param oldText   被替换的原文（rewrite 时为空）
 * @param newText   新内容
 * @param startLine 修改开始的行号（基于当时的工作副本，从 1 开始，仅用于展示）
 * @param reason    修改理由，展示给用户
 */
public record CodeEdit(String kind, String oldText, String newText, int startLine, String reason) {

    public static final String KIND_REPLACE = "replace";

    public static final String KIND_REWRITE = "rewrite";
}
