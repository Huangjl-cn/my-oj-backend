package com.hjl.oj.ai.call;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AiErrorTranslatorTest {

    @Test
    void mapsAuthenticationToInvalidKey() {
        assertEquals(ErrorCode.AI_KEY_INVALID.getCode(),
                AiErrorTranslator.translate(new AuthenticationException("401")).getCode());
    }

    @Test
    void mapsPaymentRequiredToInsufficientBalance() {
        RuntimeException wrapped = new InvalidRequestException(new HttpException(402, "Insufficient Balance"));

        assertEquals(ErrorCode.AI_BALANCE_INSUFFICIENT.getCode(), AiErrorTranslator.translate(wrapped).getCode());
    }

    @Test
    void mapsRateLimit() {
        assertEquals(ErrorCode.AI_RATE_LIMITED.getCode(),
                AiErrorTranslator.translate(new RateLimitException("429")).getCode());
    }

    @Test
    void unknownErrorsBecomeUpstreamError() {
        assertEquals(ErrorCode.AI_UPSTREAM_ERROR.getCode(),
                AiErrorTranslator.translate(new IllegalStateException("boom")).getCode());
    }

    @Test
    void businessExceptionsPassThrough() {
        BusinessException original = new BusinessException(ErrorCode.AI_BUSY);

        assertSame(original, AiErrorTranslator.translate(original));
    }
}
