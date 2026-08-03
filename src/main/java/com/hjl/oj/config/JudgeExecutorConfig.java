package com.hjl.oj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 判题任务执行器配置。
 */
@Configuration
public class JudgeExecutorConfig {

    @Bean(name = "judgeExecutor", destroyMethod = "shutdown")
    public ExecutorService judgeExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("judge-", 0).factory());
    }
}
