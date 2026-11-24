package com.hjl.oj.companion.context;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.dto.question.JudgeConfig;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionFactsBuilderTest {

    @Test
    void classifiesBothChineseAndEnglishMessages() {
        assertEquals(Verdict.ACCEPTED, VerdictClassifier.classify(submit("Accepted", 0)));
        assertEquals(Verdict.WRONG_ANSWER, VerdictClassifier.classify(submit("Wrong Answer\n输入：[1,2]", 0)));
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, VerdictClassifier.classify(submit("超时", 0)));
        assertEquals(Verdict.MEMORY_LIMIT_EXCEEDED, VerdictClassifier.classify(submit("内存溢出", 0)));
        assertEquals(Verdict.COMPILE_ERROR, VerdictClassifier.classify(submit("Compile Error\nMain.java:3", 0)));
        assertEquals(Verdict.RUNTIME_ERROR, VerdictClassifier.classify(submit("Runtime Error\nIndexOutOfBounds", 0)));
    }

    @Test
    void usesStatusForJudgingAndFailedSubmissions() {
        QuestionSubmit judging = submit("{}", 0);
        judging.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        judging.setJudgeInfo("{}");
        QuestionSubmit failed = submit("Accepted", 0);
        failed.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());

        assertEquals(Verdict.JUDGING, VerdictClassifier.classify(judging));
        assertEquals(Verdict.SYSTEM_ERROR, VerdictClassifier.classify(failed));
    }

    @Test
    void firstSubmissionHasNoPreviousVerdict() {
        SubmissionFacts facts = SubmissionFactsBuilder.build(submit("Accepted", 10), List.of(), 1, null);

        assertEquals(SubmissionFacts.Trend.FIRST, facts.trend());
        assertNull(facts.previousVerdict());
        assertNull(facts.minutesSincePrevious());
        assertEquals(1, facts.sameVerdictStreak());
    }

    @Test
    void detectsProgressFromWrongAnswerToTimeout() {
        QuestionSubmit current = submit("超时", 30);
        SubmissionFacts facts = SubmissionFactsBuilder.build(current, List.of(submit("Wrong Answer", 20)), 2, null);

        assertEquals(SubmissionFacts.Trend.IMPROVED, facts.trend());
        assertEquals(10L, facts.minutesSincePrevious());
    }

    @Test
    void countsConsecutiveSameVerdict() {
        List<QuestionSubmit> previous = List.of(submit("Wrong Answer", 20), submit("Wrong Answer", 10),
                submit("Compile Error", 5));
        SubmissionFacts facts = SubmissionFactsBuilder.build(submit("Wrong Answer", 30), previous, 4, null);

        assertEquals(SubmissionFacts.Trend.SAME, facts.trend());
        assertEquals(3, facts.sameVerdictStreak());
        assertTrue(facts.describe().contains("已经连续 3 次"));
    }

    @Test
    void notesEarlierAcceptanceAndLimits() {
        JudgeConfig judgeConfig = new JudgeConfig();
        judgeConfig.setTimeLimit(1000L);
        judgeConfig.setMemoryLimit(65536L);
        SubmissionFacts facts = SubmissionFactsBuilder.build(submit("Wrong Answer", 30),
                List.of(submit("Accepted", 20)), 2, judgeConfig);

        assertTrue(facts.acceptedBefore());
        assertEquals(SubmissionFacts.Trend.REGRESSED, facts.trend());
        assertTrue(facts.describe().contains("限制 1000 ms"));
    }

    @Test
    void truncatesLongDiagnostics() {
        String longDiagnostic = "Runtime Error\n" + "x".repeat(SubmissionFactsBuilder.MAX_DIAGNOSTIC_CHARS + 100);
        SubmissionFacts facts = SubmissionFactsBuilder.build(submit(longDiagnostic, 1), List.of(), 1, null);

        assertTrue(facts.diagnostic().endsWith("已截断）"));
    }

    private static QuestionSubmit submit(String message, int minute) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(message);
        judgeInfo.setTime(12L);
        judgeInfo.setMemory(2048L);
        QuestionSubmit submit = new QuestionSubmit();
        submit.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        submit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        submit.setCreateTime(new Date(minute * 60_000L));
        return submit;
    }
}
