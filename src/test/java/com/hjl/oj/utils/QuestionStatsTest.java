package com.hjl.oj.utils;

import com.hjl.oj.model.dto.questionsubmit.QuestionProgressRow;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.QuestionDifficultyEnum;
import com.hjl.oj.model.vo.QuestionProgressVO;
import com.hjl.oj.model.vo.QuestionTagVO;
import com.hjl.oj.model.vo.TagAttemptsVO;
import com.hjl.oj.model.vo.TagProgressVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionStatsTest {

    @Test
    void difficultyTagsAreRecognisedInBothLanguages() {
        assertEquals(Optional.of(QuestionDifficultyEnum.EASY), QuestionDifficultyEnum.fromTag(" Easy "));
        assertEquals(Optional.of(QuestionDifficultyEnum.HARD), QuestionDifficultyEnum.fromTag("困难"));
        assertEquals(Optional.empty(), QuestionDifficultyEnum.fromTag("数学"));
        assertEquals(Optional.of(QuestionDifficultyEnum.MEDIUM),
                QuestionDifficultyEnum.fromTags(List.of("图论", "中等", "hard")));
    }

    @Test
    void brokenTagJsonCountsAsNoTags() {
        assertEquals(List.of(), QuestionStats.parseTags("not json"));
        assertEquals(List.of(), QuestionStats.parseTags(null));
        assertEquals(List.of("栈", "字符串"), QuestionStats.parseTags("[\"栈\",\" \",\"字符串\"]"));
    }

    @Test
    void progressCountsTotalsSolvedAndStatuses() {
        List<Question> questions = List.of(
                question(1L, "[\"easy\",\"数学\"]"),
                question(2L, "[\"简单\"]"),
                question(3L, "[\"medium\"]"),
                question(4L, "[\"hard\"]"),
                question(5L, "[\"数学\"]"));
        List<QuestionProgressRow> rows = List.of(
                row(1L, 1, 0),
                row(2L, 0, 1),
                row(3L, 1, 1),
                row(4L, 0, 0),
                row(99L, 1, 0));

        QuestionProgressVO progress = QuestionStats.buildProgress(questions, rows);

        assertEquals(5, progress.getTotal().getAll());
        assertEquals(2, progress.getTotal().getEasy());
        assertEquals(1, progress.getTotal().getMedium());
        assertEquals(1, progress.getTotal().getHard());
        assertEquals(2, progress.getSolved().getAll());
        assertEquals(1, progress.getSolved().getEasy());
        assertEquals(1, progress.getSolved().getMedium());
        assertEquals(0, progress.getSolved().getHard());
        // 通过过就算通过，哪怕后面还有判题中的提交；已删除的题（99）不出现
        assertEquals(Map.of("1", "passed", "2", "judging", "3", "passed", "4", "failed"), progress.getStatuses());
    }

    @Test
    void tagsAreCountedWithoutDifficultyAndSortedByUse() {
        List<Question> questions = List.of(
                question(1L, "[\"easy\",\"数学\",\"数学\"]"),
                question(2L, "[\"字符串\",\"栈\"]"),
                question(3L, "[\"hard\",\"字符串\"]"));

        List<QuestionTagVO> tags = QuestionStats.countTags(questions);

        assertEquals(List.of(
                new QuestionTagVO("字符串", 2),
                new QuestionTagVO("数学", 1),
                new QuestionTagVO("栈", 1)), tags);
    }

    @Test
    void tagProgressSplitsSolvedAndAttemptedPerTag() {
        List<Question> questions = List.of(
                question(1L, "[\"easy\",\"数组\",\"哈希表\"]"),
                question(2L, "[\"medium\",\"数组\"]"),
                question(3L, "[\"数组\",\" 数组 \"]"),
                question(4L, "[\"hard\",\"图论\"]"),
                question(5L, "[\"哈希表\"]"));
        List<QuestionProgressRow> rows = List.of(
                // 1 通过；2 判题中（算做过没通过）；3 没通过；4、5 没做过；99 是已删除的题
                row(1L, 1, 0),
                row(2L, 0, 1),
                row(3L, 0, 0),
                row(99L, 1, 0));

        List<TagProgressVO> tags = QuestionStats.buildTagProgress(questions, rows);

        // 难度标签不算；同一道题重复的标签只算一次；题目数多的在前，相同时按首次出现的顺序
        assertEquals(List.of(
                new TagProgressVO("数组", 3, 1, 2),
                new TagProgressVO("哈希表", 2, 1, 0),
                new TagProgressVO("图论", 1, 0, 0)), tags);
    }

    @Test
    void tagProgressIsEmptyWithoutTags() {
        assertEquals(List.of(), QuestionStats.buildTagProgress(List.of(question(1L, null)), List.of(row(1L, 1, 0))));
    }

    private static Question question(long id, String tags) {
        Question question = new Question();
        question.setId(id);
        question.setTags(tags);
        return question;
    }

    private static QuestionProgressRow row(long questionId, int accepted, int pending) {
        QuestionProgressRow row = new QuestionProgressRow();
        row.setQuestionId(questionId);
        row.setAccepted(accepted);
        row.setPending(pending);
        return row;
    }

    @Test
    void tagAttemptsAverageThePassedQuestionsAndSkipRareTags() {
        List<Question> questions = List.of(
                question(1L, "[\"easy\",\"数组\"]"),
                question(2L, "[\"hard\",\"数组\",\"动态规划\"]"),
                question(3L, "[\"动态规划\"]"),
                question(4L, "[\"图\"]"),
                question(5L, "[\"栈\"]"));
        // 题 5 没通过；「图」只通过 1 道
        Map<Long, Integer> attempts = Map.of(1L, 1, 2L, 5, 3L, 3, 4L, 9);

        assertEquals(List.of(new TagAttemptsVO("动态规划", 2, 4.0), new TagAttemptsVO("数组", 2, 3.0)),
                QuestionStats.buildTagAttempts(questions, attempts, 2, 3));
        assertEquals(1, QuestionStats.buildTagAttempts(questions, attempts, 2, 1).size());
        // 都不够 2 道时放宽到 1 道
        assertEquals(List.of(new TagAttemptsVO("图", 1, 9.0)),
                QuestionStats.buildTagAttempts(List.of(question(4L, "[\"图\"]")), attempts, 2, 3));
    }
}
