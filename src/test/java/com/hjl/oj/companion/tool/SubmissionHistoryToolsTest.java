package com.hjl.oj.companion.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.service.QuestionSubmitService;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionHistoryToolsTest {

    private final QuestionSubmitService questionSubmitService = mock(QuestionSubmitService.class);

    private final SubmissionHistoryTools tools = new SubmissionHistoryTools(1L, 2L, questionSubmitService, 5, 4000);

    @Test
    void refusesSubmissionOfAnotherUserOrQuestion() {
        when(questionSubmitService.getById(10L)).thenReturn(submission(10L, 99L, 2L));
        when(questionSubmitService.getById(11L)).thenReturn(submission(11L, 1L, 99L));

        assertTrue(tools.getMySubmission("10").contains("只能查看用户自己在本题的提交"));
        assertTrue(tools.getMySubmission("11").contains("只能查看用户自己在本题的提交"));
        assertFalse(tools.getMySubmission("10").contains("secret code"));
    }

    @Test
    void showsOwnSubmissionWithCodeAndDiagnostic() {
        when(questionSubmitService.getById(10L)).thenReturn(submission(10L, 1L, 2L));

        String detail = tools.getMySubmission(" 10 ");

        assertTrue(detail.contains("secret code"));
        assertTrue(detail.contains("答案错误"));
        assertTrue(detail.contains("输入：[1,2]"));
    }

    @Test
    void rejectsMalformedId() {
        assertTrue(tools.getMySubmission("abc").contains("格式不对"));
        assertTrue(tools.getMySubmission(null).contains("格式不对"));
    }

    @Test
    void listsRecentSubmissions() {
        when(questionSubmitService.list(any(Wrapper.class)))
                .thenReturn(List.of(submission(10L, 1L, 2L)));

        String list = tools.listMySubmissions();

        assertTrue(list.contains("提交 id 10"));
        assertTrue(list.contains("结果：答案错误"));
        assertTrue(list.contains("耗时 12 ms"));
    }

    private static QuestionSubmit submission(long id, long userId, long questionId) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("Wrong Answer\n输入：[1,2]");
        judgeInfo.setTime(12L);
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(id);
        submit.setUserId(userId);
        submit.setQuestionId(questionId);
        submit.setLanguage("java");
        submit.setCode("secret code");
        submit.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        submit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        submit.setCreateTime(new Date());
        return submit;
    }
}
