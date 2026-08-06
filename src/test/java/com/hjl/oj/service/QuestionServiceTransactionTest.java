package com.hjl.oj.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class QuestionServiceTransactionTest {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionStarterCodeService questionStarterCodeService;

    @Test
    void questionWritesAreCalledThroughTransactionProxy() {
        assertTrue(AopUtils.isAopProxy(questionService));
        Advised advised = (Advised) questionService;
        assertTrue(Arrays.stream(advised.getAdvisors())
                .anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor));

        assertTrue(AopUtils.isAopProxy(questionStarterCodeService));
        Advised starterCodeAdvised = (Advised) questionStarterCodeService;
        assertTrue(Arrays.stream(starterCodeAdvised.getAdvisors())
                .anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor));
    }
}
