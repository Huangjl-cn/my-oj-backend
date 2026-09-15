package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitArchiveQueryRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitGroupQueryRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.QuestionSubmitVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitServiceImplArchiveTest {

    @Mock
    private QuestionService questionService;

    @Mock
    private UserService userService;

    @InjectMocks
    private QuestionSubmitServiceImpl questionSubmitService;

    @Test
    void assembleArchiveKeepsCodeAndFillsSummaries() {
        QuestionSubmit succeed = submit(11L, 100L, 200L, 2, "{\"message\":\"Accepted\",\"time\":62,\"memory\":7578}");
        succeed.setCode("class Main {}");
        QuestionSubmit failed = submit(12L, 100L, 201L, 3, "{\"message\":\"Runtime Error\"}");
        Page<QuestionSubmit> entityPage = new Page<>(1, 10, 2);
        entityPage.setRecords(List.of(succeed, failed));
        Question question = new Question();
        question.setId(100L);
        question.setTitle("两数之和");
        question.setTags("[\"easy\",\"array\"]");
        User submitUser = new User();
        submitUser.setId(200L);
        submitUser.setUserName("nelson");
        submitUser.setUserAvatar("avatar_01");
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(question));
        when(userService.listByIds(anyCollection())).thenReturn(List.of(submitUser));

        Page<QuestionSubmitVO> voPage = questionSubmitService.assembleArchiveVOPage(entityPage);

        List<QuestionSubmitVO> records = voPage.getRecords();
        assertEquals(2, records.size());
        QuestionSubmitVO succeedVO = records.get(0);
        assertEquals("class Main {}", succeedVO.getCode());
        assertEquals("accepted", succeedVO.getJudgeResult());
        assertEquals("两数之和", succeedVO.getQuestionTitle());
        assertEquals(List.of("easy", "array"), succeedVO.getQuestionTags());
        assertEquals("nelson", succeedVO.getUserName());
        assertEquals("avatar_01", succeedVO.getUserAvatar());
        assertEquals("failed", records.get(1).getJudgeResult());
    }

    @Test
    void assembleArchiveSkipsQueriesForEmptyPage() {
        Page<QuestionSubmit> entityPage = new Page<>(1, 10, 0);

        Page<QuestionSubmitVO> voPage = questionSubmitService.assembleArchiveVOPage(entityPage);

        assertTrue(voPage.getRecords().isEmpty());
        assertEquals(0, voPage.getTotal());
        verifyNoInteractions(questionService, userService);
    }

    @Test
    void archiveRejectsIllegalSortFieldJudgeResultAndOrder() {
        User loginUser = new User();
        loginUser.setId(1L);

        QuestionSubmitArchiveQueryRequest badSortField = new QuestionSubmitArchiveQueryRequest();
        badSortField.setSortField("drop table");
        BusinessException sortFieldException = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitArchiveVOPage(badSortField, loginUser));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), sortFieldException.getCode());

        QuestionSubmitArchiveQueryRequest badJudgeResult = new QuestionSubmitArchiveQueryRequest();
        badJudgeResult.setJudgeResult("weird");
        BusinessException judgeResultException = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitArchiveVOPage(badJudgeResult, loginUser));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), judgeResultException.getCode());

        QuestionSubmitArchiveQueryRequest badSortOrder = new QuestionSubmitArchiveQueryRequest();
        badSortOrder.setSortOrder("asc");
        BusinessException sortOrderException = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitArchiveVOPage(badSortOrder, loginUser));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), sortOrderException.getCode());
    }

    @Test
    void groupRejectsIllegalPageSizeSortFieldAndJudgeResult() {
        User loginUser = new User();
        loginUser.setId(1L);

        QuestionSubmitGroupQueryRequest badPageSize = new QuestionSubmitGroupQueryRequest();
        badPageSize.setPageSize(21);
        BusinessException pageSizeException = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitGroupVOPage(badPageSize, loginUser));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), pageSizeException.getCode());

        QuestionSubmitGroupQueryRequest badSortField = new QuestionSubmitGroupQueryRequest();
        badSortField.setSortField("latest_submit_time");
        BusinessException sortFieldException = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitGroupVOPage(badSortField, loginUser));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), sortFieldException.getCode());

        QuestionSubmitGroupQueryRequest badJudgeResult = new QuestionSubmitGroupQueryRequest();
        badJudgeResult.setJudgeResult("all accepted");
        BusinessException judgeResultException = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitGroupVOPage(badJudgeResult, loginUser));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), judgeResultException.getCode());
    }

    private QuestionSubmit submit(long id, long questionId, long userId, int status, String judgeInfo) {
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setId(id);
        questionSubmit.setQuestionId(questionId);
        questionSubmit.setUserId(userId);
        questionSubmit.setLanguage("java");
        questionSubmit.setStatus(status);
        questionSubmit.setJudgeInfo(judgeInfo);
        return questionSubmit;
    }
}
