package com.hjl.oj.companion.context;

import lombok.Getter;

/**
 * 陪练使用的提交结果分类。progressRank 表示离"通过"有多近，用来判断进步还是退步；小于 0 表示不参与比较。
 */
@Getter
public enum Verdict {

    ACCEPTED("通过", 4),
    TIME_LIMIT_EXCEEDED("超时", 3),
    MEMORY_LIMIT_EXCEEDED("内存超限", 3),
    WRONG_ANSWER("答案错误", 2),
    RUNTIME_ERROR("运行错误", 1),
    COMPILE_ERROR("编译错误", 0),
    SYSTEM_ERROR("系统错误", -1),
    JUDGING("判题中", -1),
    UNKNOWN("未知结果", -1);

    private final String text;

    private final int progressRank;

    Verdict(String text, int progressRank) {
        this.text = text;
        this.progressRank = progressRank;
    }

    public boolean comparable() {
        return progressRank >= 0;
    }

    public boolean terminal() {
        return this != JUDGING;
    }
}
