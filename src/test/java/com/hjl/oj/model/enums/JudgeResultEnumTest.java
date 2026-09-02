package com.hjl.oj.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JudgeResultEnumTest {

    @Test
    void fromDerivesAcceptedOnlyForExactAcceptedMessage() {
        assertEquals(JudgeResultEnum.ACCEPTED, JudgeResultEnum.from(2, "Accepted"));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(2, "Wrong Answer"));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(2, "Compile Error"));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(2, "Runtime Error"));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(2, null));
    }

    @Test
    void fromDerivesPendingForWaitingAndRunning() {
        assertEquals(JudgeResultEnum.PENDING, JudgeResultEnum.from(0, null));
        assertEquals(JudgeResultEnum.PENDING, JudgeResultEnum.from(0, "{}"));
        assertEquals(JudgeResultEnum.PENDING, JudgeResultEnum.from(1, null));
    }

    @Test
    void fromDerivesFailedForFailedStatusAndUnknownStatus() {
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(3, "Runtime Error"));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(3, null));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(99, null));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.from(null, null));
    }

    @Test
    void getEnumByValueResolvesKnownValuesOnly() {
        assertEquals(JudgeResultEnum.ALL, JudgeResultEnum.getEnumByValue("all"));
        assertEquals(JudgeResultEnum.ACCEPTED, JudgeResultEnum.getEnumByValue("accepted"));
        assertEquals(JudgeResultEnum.FAILED, JudgeResultEnum.getEnumByValue("failed"));
        assertEquals(JudgeResultEnum.PENDING, JudgeResultEnum.getEnumByValue("pending"));
        assertNull(JudgeResultEnum.getEnumByValue("weird"));
        assertNull(JudgeResultEnum.getEnumByValue(null));
    }
}
