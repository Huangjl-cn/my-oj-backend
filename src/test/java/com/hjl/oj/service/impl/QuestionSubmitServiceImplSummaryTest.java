package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.constant.CommonConstant;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.vo.QuestionSubmitSummaryVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionSubmitServiceImplSummaryTest {

    @Test
    void summaryPageKeepsVerdictButDoesNotExposeCode() {
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setId(1L);
        questionSubmit.setQuestionId(2L);
        questionSubmit.setLanguage("java");
        questionSubmit.setCode("private code");
        questionSubmit.setJudgeInfo("{\"message\":\"Accepted\",\"memory\":128,\"time\":20}");
        questionSubmit.setStatus(2);
        Page<QuestionSubmit> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(questionSubmit));

        Page<QuestionSubmitSummaryVO> result = new QuestionSubmitServiceImpl()
                .getQuestionSubmitSummaryVOPage(entityPage);

        assertEquals(1, result.getRecords().size());
        assertEquals("Accepted", result.getRecords().getFirst().getJudgeInfo().getMessage());
        assertEquals("java", result.getRecords().getFirst().getLanguage());
        assertThrows(NoSuchFieldException.class,
                () -> QuestionSubmitSummaryVO.class.getDeclaredField("code"));
    }

    @Test
    void queryWrapperRestrictsQuestionAndUser() {
        QuestionSubmitQueryRequest queryRequest = new QuestionSubmitQueryRequest();
        queryRequest.setQuestionId(2L);
        queryRequest.setUserId(1L);
        queryRequest.setSortField("createTime");
        queryRequest.setSortOrder(CommonConstant.SORT_ORDER_DESC);

        QueryWrapper<QuestionSubmit> queryWrapper = new QuestionSubmitServiceImpl()
                .getQueryWrapper(queryRequest);

        assertTrue(queryWrapper.getSqlSegment().contains("questionId"));
        assertTrue(queryWrapper.getSqlSegment().contains("userId"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(2L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1L));
    }
}
