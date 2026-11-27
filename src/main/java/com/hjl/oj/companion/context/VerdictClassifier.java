package com.hjl.oj.companion.context;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从提交状态和 judgeInfo.message 判断结果。
 *
 * <p>judgeInfo.message 的第一行是结果，其余是诊断；历史上中英文两种写法都有（如 "Wrong Answer"、"超时"），这里都认。
 */
public final class VerdictClassifier {

    private static final List<Map.Entry<Verdict, List<String>>> PREFIXES = List.of(
            Map.entry(Verdict.ACCEPTED, List.of("accepted", "成功", "通过")),
            Map.entry(Verdict.WRONG_ANSWER, List.of("wrong answer", "答案错误")),
            Map.entry(Verdict.TIME_LIMIT_EXCEEDED, List.of("time limit", "超时")),
            Map.entry(Verdict.MEMORY_LIMIT_EXCEEDED, List.of("memory", "内存")),
            Map.entry(Verdict.COMPILE_ERROR, List.of("compile", "编译错误")),
            Map.entry(Verdict.RUNTIME_ERROR, List.of("runtime", "运行错误", "output limit", "输出溢出", "dangerous", "危险操作")),
            Map.entry(Verdict.SYSTEM_ERROR, List.of("system", "系统错误")));

    private VerdictClassifier() {
    }

    public static Verdict classify(QuestionSubmit submit) {
        return classify(submit.getStatus(), message(submit));
    }

    /**
     * @param message judgeInfo.message（整段或只有第一行都行）
     */
    public static Verdict classify(Integer status, String message) {
        if (QuestionSubmitStatusEnum.WAITING.getValue().equals(status)
                || QuestionSubmitStatusEnum.RUNNING.getValue().equals(status)) {
            return Verdict.JUDGING;
        }
        if (QuestionSubmitStatusEnum.FAILED.getValue().equals(status)) {
            return Verdict.SYSTEM_ERROR;
        }
        String firstLine = firstLine(message == null ? "" : message.strip()).toLowerCase(Locale.ROOT);
        if (firstLine.isEmpty()) {
            return Verdict.UNKNOWN;
        }
        for (Map.Entry<Verdict, List<String>> entry : PREFIXES) {
            if (entry.getValue().stream().anyMatch(firstLine::startsWith)) {
                return entry.getKey();
            }
        }
        return Verdict.UNKNOWN;
    }

    /**
     * judgeInfo.message 第一行之后的诊断内容（编译错误、首个错误用例等）
     */
    public static String diagnostic(QuestionSubmit submit) {
        String message = message(submit);
        int newline = message.indexOf('\n');
        return newline < 0 ? "" : message.substring(newline + 1).strip();
    }

    public static JudgeInfo judgeInfo(QuestionSubmit submit) {
        String json = submit.getJudgeInfo();
        if (json == null || json.isBlank() || !JSONUtil.isTypeJSONObject(json)) {
            return new JudgeInfo();
        }
        return JSONUtil.toBean(json, JudgeInfo.class);
    }

    private static String message(QuestionSubmit submit) {
        String message = judgeInfo(submit).getMessage();
        return message == null ? "" : message.strip();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return (newline < 0 ? text : text.substring(0, newline)).strip();
    }
}
