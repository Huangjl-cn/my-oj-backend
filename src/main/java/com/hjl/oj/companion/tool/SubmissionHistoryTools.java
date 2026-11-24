package com.hjl.oj.companion.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hjl.oj.companion.context.VerdictClassifier;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.service.QuestionSubmitService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 给模型查当前用户在本题的提交记录。用户和题目在创建时固定，模型只能传提交 id，查不到别人的数据。
 */
public class SubmissionHistoryTools {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private static final int LIST_DIAGNOSTIC_CHARS = 200;

    private final long userId;

    private final long questionId;

    private final QuestionSubmitService questionSubmitService;

    private final int limit;

    private final int maxResultChars;

    public SubmissionHistoryTools(long userId, long questionId, QuestionSubmitService questionSubmitService,
                                  int limit, int maxResultChars) {
        this.userId = userId;
        this.questionId = questionId;
        this.questionSubmitService = questionSubmitService;
        this.limit = limit;
        this.maxResultChars = maxResultChars;
    }

    @Tool("查询用户在本题最近几次提交的结果（时间、语言、判题结果、耗时、内存、诊断摘要），最近的在前")
    public String listMySubmissions() {
        List<QuestionSubmit> submissions = questionSubmitService.list(new QueryWrapper<QuestionSubmit>()
                .select("id", "language", "judgeInfo", "status", "createTime")
                .eq("userId", userId)
                .eq("questionId", questionId)
                .orderByDesc("id")
                .last("limit " + limit));
        if (submissions.isEmpty()) {
            return "用户在本题还没有提交过";
        }
        StringBuilder text = new StringBuilder();
        for (QuestionSubmit submission : submissions) {
            JudgeInfo judgeInfo = VerdictClassifier.judgeInfo(submission);
            text.append("- 提交 id ").append(submission.getId())
                    .append("，").append(formatTime(submission.getCreateTime()))
                    .append("，").append(submission.getLanguage())
                    .append("，结果：").append(VerdictClassifier.classify(submission).getText());
            if (judgeInfo.getTime() != null) {
                text.append("，耗时 ").append(judgeInfo.getTime()).append(" ms");
            }
            if (judgeInfo.getMemory() != null) {
                text.append("，内存 ").append(judgeInfo.getMemory()).append(" KB");
            }
            String diagnostic = VerdictClassifier.diagnostic(submission);
            if (!diagnostic.isEmpty()) {
                text.append("\n  诊断：").append(truncate(diagnostic.replace("\n", " "), LIST_DIAGNOSTIC_CHARS));
            }
            text.append("\n");
        }
        return truncate(text.toString(), maxResultChars);
    }

    @Tool("查看用户在本题某一次提交的完整代码和判题诊断")
    public String getMySubmission(@P("提交 id，来自 listMySubmissions 的结果") String submissionId) {
        long id;
        try {
            id = Long.parseLong(submissionId.strip());
        } catch (NumberFormatException | NullPointerException e) {
            return "提交 id 格式不对，应该是一串数字";
        }
        QuestionSubmit submission = questionSubmitService.getById(id);
        if (submission == null || !Long.valueOf(userId).equals(submission.getUserId())
                || !Long.valueOf(questionId).equals(submission.getQuestionId())) {
            return "没有找到这次提交（只能查看用户自己在本题的提交）";
        }
        String text = "提交 id " + submission.getId() + "，" + formatTime(submission.getCreateTime())
                + "，" + submission.getLanguage() + "，结果：" + VerdictClassifier.classify(submission).getText()
                + "\n诊断：\n" + VerdictClassifier.diagnostic(submission)
                + "\n代码：\n```\n" + submission.getCode() + "\n```";
        return truncate(text, maxResultChars);
    }

    private static String formatTime(Date time) {
        return time == null ? "时间未知" : TIME_FORMAT.format(time.toInstant());
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n……（内容过长，已截断）";
    }
}
