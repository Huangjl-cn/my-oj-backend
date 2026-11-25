package com.hjl.oj.utils;

import com.hjl.oj.model.dto.questionsubmit.QuestionProgressRow;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.QuestionDifficultyEnum;
import com.hjl.oj.model.vo.QuestionProgressVO;
import com.hjl.oj.model.vo.QuestionTagVO;
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
}
