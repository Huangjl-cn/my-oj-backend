package com.hjl.oj.exception;

import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 客户端错误（4xxxx：未登录、参数错误、无权限等）是预期内的分支，记 warn 且不打堆栈，避免刷满错误日志；
     * 服务端错误（5xxxx）仍记 error 并带堆栈，便于定位抛出位置。
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        if (e.getCode() >= ErrorCode.SYSTEM_ERROR.getCode()) {
            log.error("BusinessException", e);
        } else {
            log.warn("BusinessException: code={}, message={}", e.getCode(), e.getMessage());
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
