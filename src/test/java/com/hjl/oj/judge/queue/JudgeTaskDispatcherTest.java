package com.hjl.oj.judge.queue;

import com.hjl.oj.judge.JudgeService;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.service.QuestionSubmitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeTaskDispatcherTest {

    @Mock
    private QuestionSubmitService questionSubmitService;

    @Test
    void dispatchRespectsConcurrencyLimitAndDrainsQueue() throws Exception {
        // 10 条待判题提交、并发上限 3：模拟多轮轮询直至排空，在跑判题数不得超过许可数
        Queue<QuestionSubmit> waiting = queueOf(10);
        when(questionSubmitService.getNextWaitingSubmission()).thenAnswer(invocation -> waiting.poll());
        CountingJudgeService countingJudgeService = new CountingJudgeService(10, 50);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            JudgeTaskDispatcher dispatcher = newDispatcher(executor, 3, 10, countingJudgeService);
            long deadline = System.currentTimeMillis() + 15_000;
            while (countingJudgeService.allDone.getCount() > 0) {
                assertTrue(System.currentTimeMillis() < deadline, "判题任务未在时限内排空");
                dispatcher.dispatchWaitingTasks();
                // 等待许可随判题完成而释放，再进入下一轮轮询
                countingJudgeService.allDone.await(20, TimeUnit.MILLISECONDS);
            }
        }

        assertEquals(10, countingJudgeService.processedIds.size());
        assertEquals(0, countingJudgeService.running.get());
        assertTrue(countingJudgeService.maxRunning.get() <= 3,
                "同时在跑的判题数超过并发上限：max=" + countingJudgeService.maxRunning.get());
    }

    @Test
    void dispatchJudgesWaitingSubmissionsInSubmissionOrder() throws Exception {
        // 单线程执行器保证任务串行完成，验证派发顺序严格按 id 升序（即提交顺序）
        Queue<QuestionSubmit> waiting = queueOf(5);
        when(questionSubmitService.getNextWaitingSubmission()).thenAnswer(invocation -> waiting.poll());
        CountingJudgeService countingJudgeService = new CountingJudgeService(5, 0);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            JudgeTaskDispatcher dispatcher = newDispatcher(executor, 2, 10, countingJudgeService);
            // 单轮 tick 只派发当前空闲许可数条任务，生产环境靠调度器反复轮询，这里循环直至排空
            long deadline = System.currentTimeMillis() + 10_000;
            while (countingJudgeService.allDone.getCount() > 0) {
                assertTrue(System.currentTimeMillis() < deadline, "判题任务未在时限内排空");
                dispatcher.dispatchWaitingTasks();
                countingJudgeService.allDone.await(20, TimeUnit.MILLISECONDS);
            }
            // 队列已空后再轮询一轮：验证许可正常归还、空闲轮询直接返回
            dispatcher.dispatchWaitingTasks();
        }

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), countingJudgeService.processedIds);
    }

    @Test
    void dispatchRecoversWhenPollingFails() throws Exception {
        // 第一轮取队列异常：归还许可并返回，不能让许可泄漏导致后续轮询失效
        Queue<QuestionSubmit> waiting = queueOf(5);
        when(questionSubmitService.getNextWaitingSubmission())
                .thenThrow(new RuntimeException("db down"))
                .thenAnswer(invocation -> waiting.poll());
        CountingJudgeService countingJudgeService = new CountingJudgeService(5, 0);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            JudgeTaskDispatcher dispatcher = newDispatcher(executor, 2, 10, countingJudgeService);
            dispatcher.dispatchWaitingTasks();
            // 第二轮起查询恢复正常：反复轮询直至排空，证明异常未泄漏许可
            long deadline = System.currentTimeMillis() + 10_000;
            while (countingJudgeService.allDone.getCount() > 0) {
                assertTrue(System.currentTimeMillis() < deadline, "判题任务未在时限内排空");
                dispatcher.dispatchWaitingTasks();
                countingJudgeService.allDone.await(20, TimeUnit.MILLISECONDS);
            }
        }

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), countingJudgeService.processedIds);
    }

    @Test
    void dispatchStopsWhenQueueEmpty() throws Exception {
        when(questionSubmitService.getNextWaitingSubmission()).thenReturn(null);
        CountingJudgeService countingJudgeService = new CountingJudgeService(0, 0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            JudgeTaskDispatcher dispatcher = newDispatcher(executor, 2, 10, countingJudgeService);
            dispatcher.dispatchWaitingTasks();
        }

        assertEquals(0, countingJudgeService.processedIds.size());
    }

    @Test
    void recoverStaleRunningSubmissionsResetsWithConfiguredThreshold() {
        JudgeTaskDispatcher dispatcher = newDispatcher(Executors.newSingleThreadExecutor(), 2, 10, unusedJudgeService());

        dispatcher.recoverStaleRunningSubmissions();

        ArgumentCaptor<Date> thresholdCaptor = ArgumentCaptor.forClass(Date.class);
        verify(questionSubmitService).resetStaleRunningSubmissions(thresholdCaptor.capture());
        long expectedThreshold = System.currentTimeMillis() - 10 * 60_000L;
        assertTrue(Math.abs(thresholdCaptor.getValue().getTime() - expectedThreshold) < 2_000,
                "卡死阈值应为 now - stale-running-minutes");
    }

    @Test
    void recoverStaleRunningSubmissionsSkipsWhenDisabled() {
        JudgeTaskDispatcher dispatcher = newDispatcher(Executors.newSingleThreadExecutor(), 2, 0, unusedJudgeService());

        dispatcher.recoverStaleRunningSubmissions();

        verify(questionSubmitService, never()).resetStaleRunningSubmissions(any());
    }

    /**
     * 构造完成注入的调度器：字段均为私有 + @Value/@Resource 注入，统一走反射赋值
     */
    private JudgeTaskDispatcher newDispatcher(ExecutorService executor, int concurrency,
                                              int staleRunningMinutes, JudgeService judgeService) {
        JudgeTaskDispatcher dispatcher = new JudgeTaskDispatcher();
        ReflectionTestUtils.setField(dispatcher, "questionSubmitService", questionSubmitService);
        ReflectionTestUtils.setField(dispatcher, "judgeService", judgeService);
        ReflectionTestUtils.setField(dispatcher, "judgeExecutor", executor);
        ReflectionTestUtils.setField(dispatcher, "concurrency", concurrency);
        ReflectionTestUtils.setField(dispatcher, "staleRunningMinutes", staleRunningMinutes);
        dispatcher.init();
        return dispatcher;
    }

    private Queue<QuestionSubmit> queueOf(int count) {
        Queue<QuestionSubmit> waiting = new LinkedList<>();
        for (long id = 1; id <= count; id++) {
            QuestionSubmit questionSubmit = new QuestionSubmit();
            questionSubmit.setId(id);
            waiting.add(questionSubmit);
        }
        return waiting;
    }

    /**
     * 卡死恢复测试不需要真实判题服务，占位即可
     */
    private JudgeService unusedJudgeService() {
        return questionSubmitId -> null;
    }

    /**
     * 判题服务替身：记录处理顺序与同时在跑峰值，替代并发调用下线程安全性存疑的 Mockito mock
     */
    private static final class CountingJudgeService implements JudgeService {
        private final List<Long> processedIds = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger maxRunning = new AtomicInteger();
        private final CountDownLatch allDone;
        private final long judgeMillis;

        private CountingJudgeService(int expectedCount, long judgeMillis) {
            this.allDone = new CountDownLatch(expectedCount);
            this.judgeMillis = judgeMillis;
        }

        @Override
        public QuestionSubmit processSubmission(long questionSubmitId) {
            int current = running.incrementAndGet();
            maxRunning.accumulateAndGet(current, Math::max);
            processedIds.add(questionSubmitId);
            if (judgeMillis > 0) {
                try {
                    Thread.sleep(judgeMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            running.decrementAndGet();
            allDone.countDown();
            return null;
        }
    }
}
