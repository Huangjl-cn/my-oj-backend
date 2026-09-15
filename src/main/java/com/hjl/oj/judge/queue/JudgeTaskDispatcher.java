package com.hjl.oj.judge.queue;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.judge.JudgeService;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.service.QuestionSubmitService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * 判题任务调度器：question_submit 表即队列（status=WAITING 的记录按 id 升序即提交顺序），
 * 定时轮询 + 有界并发派发，实现削峰填谷。
 *
 * <p>高峰期提交只落库排队（用户看到排队人数），沙箱侧同时在跑的判题数被许可数限制，
 * 内存不再随提交量线性增长；低谷期轮询自动消化积压。进程重启后遗留的 WAITING 记录
 * 由轮询自动续判，判题中途进程中断遗留的 RUNNING 记录由
 * {@link #recoverStaleRunningSubmissions()} 重置重判（at-least-once）。
 * 多实例部署时依靠 WAITING→RUNNING 原子抢占（updateStatusIfCurrent）防重复判题。
 */
@Component
@Slf4j
public class JudgeTaskDispatcher {

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeService judgeService;

    @Resource(name = "judgeExecutor")
    private ExecutorService judgeExecutor;

    /**
     * 判题并发上限：限制同时在跑的沙箱调用数，取值按沙箱侧可用内存与容器峰值内存估算
     */
    @Value("${judge.queue.concurrency:4}")
    private int concurrency;

    /**
     * 卡死 RUNNING 的判定分钟数：判题单次最长耗时受 codesandbox.timeout 约束（默认 60s），
     * 超过该时长仍是 RUNNING 的记录只可能是进程中断遗留。0 表示关闭卡死恢复。
     */
    @Value("${judge.queue.stale-running-minutes:10}")
    private int staleRunningMinutes;

    /**
     * 判题并发许可：派发前获取、判题线程 finally 归还
     */
    private Semaphore permits;

    @PostConstruct
    public void init() {
        ThrowUtils.throwIf(concurrency <= 0, ErrorCode.SYSTEM_ERROR, "judge.queue.concurrency 必须 > 0");
        permits = new Semaphore(concurrency);
    }

    /**
     * 轮询派发：按 id 升序（即提交顺序）逐条领取 WAITING 记录，有空闲许可就派发判题。
     * 单一轮派发入口保证排队公平：排队状态接口展示的位数与实际派发顺序一致；
     * 同时承担启动恢复——进程重启后遗留的 WAITING 记录在启动后的第一轮被拾起。
     */
    @Scheduled(fixedDelayString = "${judge.queue.poll-interval-ms:1000}")
    public void dispatchWaitingTasks() {
        while (permits.tryAcquire()) {
            QuestionSubmit next;
            try {
                next = questionSubmitService.getNextWaitingSubmission();
            } catch (Exception e) {
                permits.release();
                log.error("轮询待判题任务失败", e);
                return;
            }
            if (next == null) {
                permits.release();
                return;
            }
            long questionSubmitId = next.getId();
            try {
                judgeExecutor.execute(() -> {
                    try {
                        judgeService.processSubmission(questionSubmitId);
                    } catch (Exception e) {
                        log.error("判题任务执行失败，questionSubmitId={}", questionSubmitId, e);
                    } finally {
                        permits.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                permits.release();
                log.error("判题任务派发失败，questionSubmitId={}", questionSubmitId, e);
                return;
            }
        }
    }

    /**
     * 卡死恢复：RUNNING 且 updateTime 早于阈值的记录说明判题进程中断、结果已丢失，
     * 重置回 WAITING 由后续轮询重判。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recoverStaleRunningSubmissions() {
        if (staleRunningMinutes <= 0) {
            return;
        }
        Date staleThreshold = new Date(System.currentTimeMillis() - staleRunningMinutes * 60_000L);
        try {
            boolean recovered = questionSubmitService.resetStaleRunningSubmissions(staleThreshold);
            if (recovered) {
                log.warn("回收卡死的判题中记录并重置为待判题，threshold={}", staleThreshold);
            }
        } catch (Exception e) {
            log.error("回收卡死判题记录失败，threshold={}", staleThreshold, e);
        }
    }
}
