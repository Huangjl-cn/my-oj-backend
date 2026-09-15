package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.vo.QuestionSubmitQueueStatusVO;
import com.hjl.oj.service.QuestionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmitServiceImplQueueStatusTest {

    @Mock
    private QuestionService questionService;

    @Mock
    private QuestionSubmitMapper questionSubmitMapper;

    @Captor
    private ArgumentCaptor<QueryWrapper<QuestionSubmit>> queryWrapperCaptor;

    @Captor
    private ArgumentCaptor<LambdaUpdateWrapper<QuestionSubmit>> updateWrapperCaptor;

    @InjectMocks
    private QuestionSubmitServiceImpl questionSubmitService;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 解析方法引用需要实体的 TableInfo 缓存，单测无 MP 启动流程，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                QuestionSubmit.class);
    }

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 是泛型字段（擦除后为 Object），Mockito 无法按类型注入，需手动反射注入
        ReflectionTestUtils.setField(questionSubmitService, "baseMapper", questionSubmitMapper);
    }

    @Test
    void queueStatusCountsWaitingAheadBySubmissionOrder() {
        // 待判题提交：前方人数 = 比本人 id 小的 WAITING 数（两次 selectCount 依次为队列总长、前方人数）
        QuestionSubmit submit = waitingSubmit(20L);
        when(questionSubmitMapper.selectById(20L)).thenReturn(submit);
        when(questionSubmitMapper.selectCount(any())).thenReturn(5L, 3L);

        QuestionSubmitQueueStatusVO queueStatusVO = questionSubmitService.getQuestionSubmitQueueStatus(20L);

        assertEquals(20L, queueStatusVO.getSubmissionId());
        assertEquals(0, queueStatusVO.getStatus());
        assertEquals(5L, queueStatusVO.getQueueLength());
        assertEquals(3L, queueStatusVO.getAheadCount());
    }

    @Test
    void queueStatusZeroAheadWhenNotWaiting() {
        // 已判题完成：不在队列中，前方人数为 0，仅查询一次队列总长
        QuestionSubmit submit = waitingSubmit(21L);
        submit.setStatus(2);
        when(questionSubmitMapper.selectById(21L)).thenReturn(submit);
        when(questionSubmitMapper.selectCount(any())).thenReturn(2L);

        QuestionSubmitQueueStatusVO queueStatusVO = questionSubmitService.getQuestionSubmitQueueStatus(21L);

        assertEquals(2, queueStatusVO.getStatus());
        assertEquals(2L, queueStatusVO.getQueueLength());
        assertEquals(0L, queueStatusVO.getAheadCount());
    }

    @Test
    void queueStatusThrowsWhenSubmissionNotFound() {
        when(questionSubmitMapper.selectById(99L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionSubmitService.getQuestionSubmitQueueStatus(99L));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    @Test
    void getNextWaitingSubmissionReadsOldestWaitingIdOnly() {
        // 队头读取只取 id 且 LIMIT 1，避免读出大字段 code，也不受并发插入影响
        QuestionSubmit head = waitingSubmit(7L);
        when(questionSubmitMapper.selectOne(any())).thenReturn(head);

        QuestionSubmit next = questionSubmitService.getNextWaitingSubmission();

        assertEquals(7L, next.getId());
        verify(questionSubmitMapper).selectOne(queryWrapperCaptor.capture());
        assertEquals("id", queryWrapperCaptor.getValue().getSqlSelect());
        assertTrue(queryWrapperCaptor.getValue().getSqlSegment().contains("LIMIT 1"));
    }

    @Test
    void resetStaleRunningSubmissionsDelegatesToUpdate() {
        when(questionSubmitMapper.update(isNull(), any())).thenReturn(3);
        Date staleThreshold = new Date(System.currentTimeMillis() - 600_000L);

        boolean reset = questionSubmitService.resetStaleRunningSubmissions(staleThreshold);

        assertTrue(reset);
        verify(questionSubmitMapper).update(isNull(), updateWrapperCaptor.capture());
        assertTrue(updateWrapperCaptor.getValue().getSqlSet().contains("status="));
    }

    private QuestionSubmit waitingSubmit(long id) {
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setId(id);
        questionSubmit.setStatus(0);
        return questionSubmit;
    }
}
