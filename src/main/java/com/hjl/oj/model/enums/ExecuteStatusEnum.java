package com.hjl.oj.model.enums;

import lombok.Getter;

@Getter
public enum ExecuteStatusEnum {

    ACCEPTED("执行成功", 1),
    COMPILE_ERROR("编译错误", 2),
    RUNTIME_ERROR("运行错误", 3),
    SYSTEM_ERROR("系统错误", 4);

    private final String text;
    private final Integer value;

    ExecuteStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }
}