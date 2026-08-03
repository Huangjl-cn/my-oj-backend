package com.hjl.oj.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeExecutorConfigTest {

    @Test
    void judgeExecutorUsesVirtualThreads() throws Exception {
        JudgeExecutorConfig config = new JudgeExecutorConfig();

        try (ExecutorService executor = config.judgeExecutor()) {
            assertTrue(executor.submit(() -> Thread.currentThread().isVirtual()).get());
        }
    }
}
