package com.hjl.oj.companion.context;

import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.dto.question.JudgeConfig;
import com.hjl.oj.model.entity.QuestionSubmit;

import java.util.List;

/**
 * 计算一次提交的事实：第几次、和上次比是进是退、是否连续犯同一个错、离上次多久。
 */
public final class SubmissionFactsBuilder {

    static final int MAX_DIAGNOSTIC_CHARS = 1500;

    private SubmissionFactsBuilder() {
    }

    /**
     * @param current       本次提交
     * @param previous      本次之前的提交，按时间倒序（最近的在前）
     * @param attemptNumber 本次是第几次提交
     * @param judgeConfig   题目限制，可为 null
     */
    public static SubmissionFacts build(QuestionSubmit current, List<QuestionSubmit> previous, int attemptNumber,
                                        JudgeConfig judgeConfig) {
        Verdict verdict = VerdictClassifier.classify(current);
        List<Verdict> previousVerdicts = previous.stream().map(VerdictClassifier::classify).toList();
        Verdict previousVerdict = previousVerdicts.isEmpty() ? null : previousVerdicts.get(0);

        int streak = 1;
        for (Verdict earlier : previousVerdicts) {
            if (earlier != verdict) {
                break;
            }
            streak++;
        }

        Long minutesSincePrevious = null;
        if (!previous.isEmpty() && previous.get(0).getCreateTime() != null && current.getCreateTime() != null) {
            long millis = current.getCreateTime().getTime() - previous.get(0).getCreateTime().getTime();
            minutesSincePrevious = Math.max(0, millis / 60_000);
        }

        JudgeInfo judgeInfo = VerdictClassifier.judgeInfo(current);
        String diagnostic = VerdictClassifier.diagnostic(current);
        if (diagnostic.length() > MAX_DIAGNOSTIC_CHARS) {
            diagnostic = diagnostic.substring(0, MAX_DIAGNOSTIC_CHARS) + "\n……（诊断过长，已截断）";
        }
        return new SubmissionFacts(
                attemptNumber,
                verdict,
                previousVerdict,
                trend(verdict, previousVerdict),
                streak,
                minutesSincePrevious,
                previousVerdicts.contains(Verdict.ACCEPTED),
                judgeInfo.getTime(),
                judgeInfo.getMemory(),
                judgeConfig == null ? null : judgeConfig.getTimeLimit(),
                judgeConfig == null ? null : judgeConfig.getMemoryLimit(),
                diagnostic);
    }

    static SubmissionFacts.Trend trend(Verdict current, Verdict previous) {
        if (previous == null) {
            return SubmissionFacts.Trend.FIRST;
        }
        if (!current.comparable() || !previous.comparable()) {
            return SubmissionFacts.Trend.NOT_COMPARABLE;
        }
        if (current == previous) {
            return SubmissionFacts.Trend.SAME;
        }
        if (current.getProgressRank() > previous.getProgressRank()) {
            return SubmissionFacts.Trend.IMPROVED;
        }
        if (current.getProgressRank() < previous.getProgressRank()) {
            return SubmissionFacts.Trend.REGRESSED;
        }
        // 同为第 3 档（超时、内存超限）但种类不同
        return SubmissionFacts.Trend.NOT_COMPARABLE;
    }
}
