package com.hjl.oj.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

/**
 * 判题结果筛选枚举（提交归档页）
 * <p>accepted 仅表示 status=2 且 judgeInfo.message 精确为 "Accepted"；
 * status=2 只代表判题流程完成，其余完成态均归入 failed。
 * sqlFragment 为服务端常量片段（camelCase 列名），供 QueryWrapper.apply 与聚合 SQL 拼接，禁止拼接客户端输入。
 */
@Getter
public enum JudgeResultEnum {

    ACCEPTED("accepted",
            "(status = 2 AND JSON_UNQUOTE(JSON_EXTRACT(judgeInfo, '$.message')) = 'Accepted')"),
    FAILED("failed",
            "(status = 3 OR (status = 2 AND (JSON_UNQUOTE(JSON_EXTRACT(judgeInfo, '$.message')) IS NULL "
                    + "OR JSON_UNQUOTE(JSON_EXTRACT(judgeInfo, '$.message')) <> 'Accepted')))"),
    PENDING("pending", "status IN (0, 1)"),
    ALL("all", "");

    private final String value;

    /**
     * 筛选 SQL 条件片段（不含前导 AND，ALL 为空串）
     */
    private final String sqlFragment;

    JudgeResultEnum(String value, String sqlFragment) {
        this.value = value;
        this.sqlFragment = sqlFragment;
    }

    /**
     * 根据 value 获取枚举
     */
    public static JudgeResultEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (JudgeResultEnum anEnum : JudgeResultEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 按提交状态和判题消息派生判题结果（派生结果不会为 ALL）
     */
    public static JudgeResultEnum from(Integer status, String judgeInfoMessage) {
        if (QuestionSubmitStatusEnum.SUCCEED.getValue().equals(status)) {
            return JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfoMessage) ? ACCEPTED : FAILED;
        }
        if (QuestionSubmitStatusEnum.WAITING.getValue().equals(status)
                || QuestionSubmitStatusEnum.RUNNING.getValue().equals(status)) {
            return PENDING;
        }
        return FAILED;
    }
}
