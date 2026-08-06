package com.hjl.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.constant.CommonConstant;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.QuestionSubmitSummaryVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionControllerMySubmissionTest {

    private static final long LOGIN_USER_ID = 1L;

    @Mock
    private QuestionService questionService;

    @Mock
    private QuestionSubmitService questionSubmitService;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private QuestionController questionController;

    @Test
    @SuppressWarnings("unchecked")
    void mySubmissionListOverridesRequestedUserId() {
        User loginUser = loginUser();
        QuestionSubmitQueryRequest queryRequest = new QuestionSubmitQueryRequest();
        queryRequest.setQuestionId(2L);
        queryRequest.setUserId(999L);
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        Page<QuestionSubmit> entityPage = new Page<>(1, 10, 0);
        Page<QuestionSubmitSummaryVO> summaryPage = new Page<>(1, 10, 0);
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(questionSubmitService.getQueryWrapper(same(queryRequest))).thenReturn(queryWrapper);
        when(questionSubmitService.page(any(Page.class), same(queryWrapper))).thenReturn(entityPage);
        when(questionSubmitService.getQuestionSubmitSummaryVOPage(entityPage)).thenReturn(summaryPage);

        questionController.listMyQuestionSubmitByPage(queryRequest, request);

        assertEquals(LOGIN_USER_ID, queryRequest.getUserId());
        assertEquals("createTime", queryRequest.getSortField());
        assertEquals(CommonConstant.SORT_ORDER_DESC, queryRequest.getSortOrder());
    }

    @Test
    void mySubmissionDetailDoesNotFallBackToIdOnlyLookup() {
        when(userService.getLoginUser(request)).thenReturn(loginUser());
        when(questionSubmitService.getByIdAndUserId(7L, LOGIN_USER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionController.getMyQuestionSubmitById(7L, request));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        verify(questionSubmitService).getByIdAndUserId(7L, LOGIN_USER_ID);
    }

    private User loginUser() {
        User user = new User();
        user.setId(LOGIN_USER_ID);
        return user;
    }
}
