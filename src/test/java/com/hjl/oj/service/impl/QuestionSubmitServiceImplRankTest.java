package com.hjl.oj.service.impl;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.JudgeService;
import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitRankStatsRow;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.JudgeResultEnum;
import com.hjl.oj.model.vo.QuestionSubmitRankVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitServiceImplRankTest {

    @Mock
    private QuestionService questionService;

    @Mock
    private UserService userService;

    @Mock
    private JudgeService judgeService;

    @Mock
    private ExecutorService judgeExecutor;

    @Mock
    private QuestionSubmitMapper questionSubmitMapper;

    @InjectMocks
    private QuestionSubmitServiceImpl questionSubmitService;

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 是泛型字段（擦除后为 Object），Mockito 无法按类型注入，需手动反射注入
        ReflectionTestUtils.setField(questionSubmitService, "baseMapper", questionSubmitMapper);
    }

    @Test
    void rankReturnsCountsFromStats() {
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(11L);
        submit.setQuestionId(100L);
        submit.setLanguage("java");
        submit.setStatus(2);
        submit.setJudgeInfo("{\"message\":\"Accepted\",\"time\":100,\"memory\":8000}");
        QuestionSubmitRankStatsRow statsRow = new QuestionSubmitRankStatsRow();
        statsRow.setTimeTotal(3L);
        statsRow.setTimeBeaten(1L);
        statsRow.setMemoryTotal(4L);
        statsRow.setMemoryBeaten(2L);
        when(questionSubmitMapper.selectById(11L)).thenReturn(submit);
        when(questionSubmitMapper.selectQuestionLanguageRankStats(
                eq(JudgeResultEnum.ACCEPTED.getSqlFragment()), eq(100L), eq("java"), eq(100L), eq(8000L)))
                .thenReturn(statsRow);

        QuestionSubmitRankVO rankVO = questionSubmitService.getQuestionSubmitRank(11L);

        assertEquals(100L, rankVO.getQuestionId());
        assertEquals("java", rankVO.getLanguage());
        assertEquals(100L, rankVO.getTime());
        assertEquals(8000L, rankVO.getMemory());
        assertEquals(3L, rankVO.getTimeTotal());
        assertEquals(1L, rankVO.getTimeBeaten());
        assertEquals(4L, rankVO.getMemoryTotal());
        assertEquals(2L, rankVO.getMemoryBeaten());
    }

    @Test
    void rankFirstSubmissionCountsSelf() {
        // 首次通过提交（该题该语言仅本人一条 Accepted）：含本人的口径下应为 1/1，即超过 100%
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(13L);
        submit.setQuestionId(100L);
        submit.setLanguage("java");
        submit.setStatus(2);
        submit.setJudgeInfo("{\"message\":\"Accepted\",\"time\":100,\"memory\":8000}");
        QuestionSubmitRankStatsRow statsRow = new QuestionSubmitRankStatsRow();
        statsRow.setTimeTotal(1L);
        statsRow.setTimeBeaten(1L);
        statsRow.setMemoryTotal(1L);
        statsRow.setMemoryBeaten(1L);
        when(questionSubmitMapper.selectById(13L)).thenReturn(submit);
        when(questionSubmitMapper.selectQuestionLanguageRankStats(
                eq(JudgeResultEnum.ACCEPTED.getSqlFragment()), eq(100L), eq("java"), eq(100L), eq(8000L)))
                .thenReturn(statsRow);

        QuestionSubmitRankVO rankVO = questionSubmitService.getQuestionSubmitRank(13L);

        assertEquals(1L, rankVO.getTimeTotal());
        assertEquals(1L, rankVO.getTimeBeaten());
        assertEquals(1L, rankVO.getMemoryTotal());
        assertEquals(1L, rankVO.getMemoryBeaten());
    }

    @Test
    void rankWithoutMetricsSkipsStatsQuery() {
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(12L);
        submit.setQuestionId(100L);
        submit.setLanguage("java");
        submit.setStatus(0);
        submit.setJudgeInfo("{}");
        when(questionSubmitMapper.selectById(12L)).thenReturn(submit);

        QuestionSubmitRankVO rankVO = questionSubmitService.getQuestionSubmitRank(12L);

        assertNull(rankVO.getTime());
        assertNull(rankVO.getMemory());
        assertNull(rankVO.getTimeTotal());
        assertNull(rankVO.getTimeBeaten());
        assertNull(rankVO.getMemoryTotal());
        assertNull(rankVO.getMemoryBeaten());
        verify(questionSubmitMapper, never()).selectQuestionLanguageRankStats(any(), anyLong(), any(), any(), any());
    }

    @Test
    void rankThrowsWhenSubmissionNotFound() {
        when(questionSubmitMapper.selectById(99L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitRank(99L));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }
}
