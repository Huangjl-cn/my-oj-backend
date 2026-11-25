package com.hjl.oj.service.impl;

import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.dto.questionsubmit.QuestionProgressRow;
import com.hjl.oj.model.enums.JudgeResultEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitServiceImplProgressTest {

    @Mock
    private QuestionSubmitMapper questionSubmitMapper;

    @InjectMocks
    private QuestionSubmitServiceImpl questionSubmitService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(questionSubmitService, "baseMapper", questionSubmitMapper);
    }

    @Test
    void solvedIdsAreTheQuestionsWithAnAcceptedSubmission() {
        when(questionSubmitMapper.selectUserQuestionProgress(JudgeResultEnum.ACCEPTED.getSqlFragment(), 7L))
                .thenReturn(List.of(row(1L, 1, 0), row(2L, 0, 1), row(3L, 1, 1), row(4L, null, 0)));

        assertEquals(List.of(1L, 3L), questionSubmitService.listSolvedQuestionIds(7L));
    }

    private static QuestionProgressRow row(long questionId, Integer accepted, int pending) {
        QuestionProgressRow row = new QuestionProgressRow();
        row.setQuestionId(questionId);
        row.setAccepted(accepted);
        row.setPending(pending);
        return row;
    }
}
