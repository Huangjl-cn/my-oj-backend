package com.hjl.oj.ai.call;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;

import java.net.http.HttpTimeoutException;

/**
 * 把模型调用的异常翻译成给用户看的业务错误（Key 无效、余额不足、限流、超时等）。
 */
public final class AiErrorTranslator {

    private static final int HTTP_PAYMENT_REQUIRED = 402;

    private AiErrorTranslator() {
    }

    public static BusinessException translate(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException;
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof AuthenticationException) {
                return new BusinessException(ErrorCode.AI_KEY_INVALID);
            }
            if (current instanceof RateLimitException) {
                return new BusinessException(ErrorCode.AI_RATE_LIMITED);
            }
            if (current instanceof TimeoutException || current instanceof HttpTimeoutException) {
                return new BusinessException(ErrorCode.AI_UPSTREAM_ERROR, "AI 响应超时，请稍后再试");
            }
            if (current instanceof HttpException httpException) {
                int status = httpException.statusCode();
                if (status == HTTP_PAYMENT_REQUIRED) {
                    return new BusinessException(ErrorCode.AI_BALANCE_INSUFFICIENT);
                }
                if (status == 401 || status == 403) {
                    return new BusinessException(ErrorCode.AI_KEY_INVALID);
                }
                if (status == 429) {
                    return new BusinessException(ErrorCode.AI_RATE_LIMITED);
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return new BusinessException(ErrorCode.AI_UPSTREAM_ERROR);
    }
}
