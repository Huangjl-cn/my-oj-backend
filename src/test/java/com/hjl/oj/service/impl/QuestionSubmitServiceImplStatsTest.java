package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.dto.questionsubmit.FirstAcceptedRow;
import com.hjl.oj.model.dto.questionsubmit.LanguageStatRow;
import com.hjl.oj.model.dto.questionsubmit.QuestionProgressRow;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.JudgeResultEnum;
import com.hjl.oj.model.vo.LanguageStatVO;
import com.hjl.oj.model.vo.LearningStatsVO;
import com.hjl.oj.model.vo.SolvedQuestionVO;
import com.hjl.oj.model.vo.TagProgressVO;
import com.hjl.oj.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitServiceImplStatsTest {

    private static final String ACCEPTED = JudgeResultEnum.ACCEPTED.getSqlFragment();

    @Mock
    private QuestionSubmitMapper questionSubmitMapper;

    @Mock
    private QuestionService questionService;

    @InjectMocks
    private QuestionSubmitServiceImpl questionSubmitService;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(questionSubmitService, "baseMapper", questionSubmitMapper);
        user.setId(7L);
    }

    @Test
    void statsCombineTagsLanguagesAndRecentSolved() {
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(
                question(1L, "两数之和", "[\"easy\",\"数组\"]"),
                question(2L, "接雨水", "[\"hard\",\"数组\",\"栈\"]")));
        when(questionSubmitMapper.selectUserQuestionProgress(ACCEPTED, 7L))
                .thenReturn(List.of(progress(1L, 1), progress(2L, 1)));
        when(questionSubmitMapper.selectUserLanguageStats(ACCEPTED, 7L)).thenReturn(List.of(
                language("java", 9L, 3L, 2L),
                language("pascal", 1L, null, null)));
        Date later = new Date(2_000_000L);
        Date earlier = new Date(1_000_000L);
        when(questionSubmitMapper.selectUserFirstAccepted(ACCEPTED, 7L)).thenReturn(List.of(
                firstAccepted(2L, later, "cpp", 4L),
                firstAccepted(1L, earlier, "java", 1L)));

        LearningStatsVO stats = questionSubmitService.getMyLearningStats(user);

        assertEquals(List.of(new TagProgressVO("数组", 2, 2, 0), new TagProgressVO("栈", 1, 1, 0)), stats.getTags());
        // 不再支持的语言显示标识本身，缺的计数按 0
        assertEquals(List.of(
                new LanguageStatVO("java", "Java 25", 9, 3, 2),
                new LanguageStatVO("pascal", "pascal", 1, 0, 0)), stats.getLanguages());
        assertEquals(List.of(
                new SolvedQuestionVO(2L, "接雨水", List.of("hard", "数组", "栈"), later, "C++ 17 (GCC 15)", 4),
                new SolvedQuestionVO(1L, "两数之和", List.of("easy", "数组"), earlier, "Java 25", 1)),
                stats.getRecentSolved());
        assertEquals(1, stats.getFirstTrySolved());
        // 题目只查一次，不逐题查
        verify(questionService, times(1)).list(any(Wrapper.class));
    }

    @Test
    void recentSolvedKeepsTheLatestTenAndSkipsDeletedQuestions() {
        List<Question> questions = LongStream.rangeClosed(1, 12)
                .mapToObj(id -> question(id, "题目" + id, "[]"))
                .toList();
        when(questionService.list(any(Wrapper.class))).thenReturn(questions);
        // 按通过时间从近到远：12、99（已删除，不在题库里）、11、10 … 1
        List<FirstAcceptedRow> rows = new java.util.ArrayList<>();
        rows.add(firstAccepted(12L, new Date(1300L), "java", 1L));
        rows.add(firstAccepted(99L, new Date(1250L), "java", 1L));
        for (long id = 11; id >= 1; id--) {
            rows.add(firstAccepted(id, new Date(id * 100L), "java", 2L));
        }
        when(questionSubmitMapper.selectUserFirstAccepted(ACCEPTED, 7L)).thenReturn(rows);

        LearningStatsVO stats = questionSubmitService.getMyLearningStats(user);

        assertEquals(List.of(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L),
                stats.getRecentSolved().stream().map(SolvedQuestionVO::getQuestionId).toList());
        assertEquals(1, stats.getFirstTrySolved());
        assertEquals(List.of(), stats.getTags());
        assertEquals(List.of(), stats.getLanguages());
    }

    @Test
    void newUserGetsEmptyStats() {
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(question(1L, "两数之和", "[\"数组\"]")));

        LearningStatsVO stats = questionSubmitService.getMyLearningStats(user);

        assertEquals(List.of(new TagProgressVO("数组", 1, 0, 0)), stats.getTags());
        assertEquals(List.of(), stats.getLanguages());
        assertEquals(List.of(), stats.getRecentSolved());
        assertEquals(0, stats.getFirstTrySolved());
    }

    private static Question question(long id, String title, String tags) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(title);
        question.setTags(tags);
        return question;
    }

    private static QuestionProgressRow progress(long questionId, int accepted) {
        QuestionProgressRow row = new QuestionProgressRow();
        row.setQuestionId(questionId);
        row.setAccepted(accepted);
        row.setPending(0);
        return row;
    }

    private static LanguageStatRow language(String language, Long submits, Long accepted, Long solved) {
        LanguageStatRow row = new LanguageStatRow();
        row.setLanguage(language);
        row.setSubmitCount(submits);
        row.setAcceptedCount(accepted);
        row.setSolvedCount(solved);
        return row;
    }

    private static FirstAcceptedRow firstAccepted(long questionId, Date solvedTime, String language, Long attempts) {
        FirstAcceptedRow row = new FirstAcceptedRow();
        row.setQuestionId(questionId);
        row.setSolvedTime(solvedTime);
        row.setLanguage(language);
        row.setAttempts(attempts);
        return row;
    }
}
