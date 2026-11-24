package com.hjl.oj.common;

import lombok.Getter;

/**
 * 自定义错误码
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    AI_KEY_NOT_CONFIGURED(40020, "请先配置 AI 的 API Key"),
    AI_KEY_INVALID(40021, "API Key 无效，请检查后重新填写"),
    AI_BALANCE_INSUFFICIENT(40022, "AI 账户余额不足，请充值后再试"),
    AI_BUSY(42900, "上一条消息还在处理中，请稍候"),
    AI_RATE_LIMITED(42901, "AI 服务请求太频繁，请稍后再试"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    API_REQUEST_ERROR(50010, "接口调用失败"),
    AI_UPSTREAM_ERROR(50020, "AI 服务调用失败，请稍后再试");


    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
