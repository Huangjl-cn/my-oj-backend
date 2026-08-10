package com.hjl.oj.judge;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.model.ExecuteCaseRequest;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.JudgeCaseDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceImplTest {

    private static final long SUBMIT_ID = 1L;

    @Mock
    private QuestionService questionService;

    @Mock
    private QuestionSubmitService questionSubmitService;

    @Mock
    private JudgeManager judgeManager;

    @Mock
    private JudgeCaseDataService judgeCaseDataService;

    @Mock
    private JudgeInputEncoder judgeInputEncoder;

    @InjectMocks
    private JudgeServiceImpl judgeService;

    private QuestionSubmit questionSubmit;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(judgeService, "type", "example");
        questionSubmit = new QuestionSubmit();
        questionSubmit.setId(SUBMIT_ID);
        questionSubmit.setQuestionId(2L);
        questionSubmit.setLanguage("java");
        questionSubmit.setCode("class Main {} ");
        questionSubmit.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
        when(questionSubmitService.getById(SUBMIT_ID)).thenReturn(questionSubmit);
    }

    @Test
    void doJudgeStopsWhenSubmissionWasAlreadyClaimed() {
        when(questionSubmitService.updateStatusIfCurrent(
                SUBMIT_ID,
                QuestionSubmitStatusEnum.WAITING,
                QuestionSubmitStatusEnum.RUNNING,
                null)).thenReturn(false);

        assertThrows(BusinessException.class, () -> judgeService.processSubmission(SUBMIT_ID));

        verify(questionService, never()).getById(any());
        verify(judgeManager, never()).applyStrategy(any());
    }

    @Test
    void doJudgeMarksSubmissionFailedWhenJudgingThrows() {
        Question question = new Question();
        question.setId(2L);
        question.setJudgeCase("{}");
        prepareJudgeCases(question);
        when(questionService.getById(2L)).thenReturn(question);
        when(questionSubmitService.updateStatusIfCurrent(
                SUBMIT_ID,
                QuestionSubmitStatusEnum.WAITING,
                QuestionSubmitStatusEnum.RUNNING,
                null)).thenReturn(true);
        when(judgeManager.applyStrategy(any())).thenThrow(new IllegalStateException("judge failed"));
        when(questionSubmitService.updateStatusIfCurrent(
                eq(SUBMIT_ID),
                eq(QuestionSubmitStatusEnum.RUNNING),
                eq(QuestionSubmitStatusEnum.FAILED),
                any())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> judgeService.processSubmission(SUBMIT_ID));

        verify(questionSubmitService).updateStatusIfCurrent(
                eq(SUBMIT_ID),
                eq(QuestionSubmitStatusEnum.RUNNING),
                eq(QuestionSubmitStatusEnum.FAILED),
                argThat(json -> {
                    JudgeInfo judgeInfo = JSONUtil.toBean(json, JudgeInfo.class);
                    return JudgeInfoMessageEnum.SYSTEM_ERROR.getValue().equals(judgeInfo.getMessage());
                }));
    }

    @Test
    void doJudgeMarksSubmissionSucceedAfterJudgingCompletes() {
        Question question = new Question();
        question.setId(2L);
        question.setJudgeCase("{}");
        prepareJudgeCases(question);
        when(questionService.getById(2L)).thenReturn(question);
        when(questionSubmitService.updateStatusIfCurrent(
                SUBMIT_ID,
                QuestionSubmitStatusEnum.WAITING,
                QuestionSubmitStatusEnum.RUNNING,
                null)).thenReturn(true);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        when(judgeManager.applyStrategy(any())).thenReturn(judgeInfo);
        when(questionSubmitService.completeSubmissionAndUpdateStats(
                SUBMIT_ID,
                questionSubmit.getQuestionId(),
                JSONUtil.toJsonStr(judgeInfo),
                true)).thenReturn(questionSubmit);

        QuestionSubmit result = judgeService.processSubmission(SUBMIT_ID);

        assertEquals(questionSubmit, result);
        verify(questionSubmitService).completeSubmissionAndUpdateStats(
                SUBMIT_ID,
                questionSubmit.getQuestionId(),
                JSONUtil.toJsonStr(judgeInfo),
                true);
    }

    private void prepareJudgeCases(Question question) {
        JudgeCaseConfig judgeCaseConfig = new JudgeCaseConfig();
        when(judgeCaseDataService.deserialize(question.getJudgeCase())).thenReturn(judgeCaseConfig);
        when(judgeInputEncoder.encode(judgeCaseConfig)).thenReturn(List.of(
                ExecuteCaseRequest.builder().args(List.of("1")).build()));
    }
}
