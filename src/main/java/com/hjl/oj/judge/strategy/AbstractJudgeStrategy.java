package com.hjl.oj.judge.strategy;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.dto.question.JudgeConfig;
import com.hjl.oj.model.enums.ExecuteStatusEnum;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 判题流程模板，语言策略只覆盖确有差异的规则。
 */
public abstract class AbstractJudgeStrategy implements JudgeStrategy {

    private static final String NO_OUTPUT = "<无输出>";

    @Override
    public final JudgeInfo evaluate(JudgeContext judgeContext) {
        JudgeInfo executeInfo = judgeContext.getJudgeInfo();
        long memory = executeInfo == null ? 0L : Optional.ofNullable(executeInfo.getMemory()).orElse(0L);
        long time = executeInfo == null ? 0L : Optional.ofNullable(executeInfo.getTime()).orElse(0L);
        JudgeInfo result = new JudgeInfo();
        result.setMemory(memory);
        result.setTime(time);

        JudgeInfoMessageEnum executionFailure = getExecutionFailure(judgeContext.getExecuteStatus());
        if (executionFailure != null) {
            String sandboxMessage = executeInfo == null ? null : executeInfo.getMessage();
            result.setMessage(StringUtils.defaultIfBlank(sandboxMessage, executionFailure.getValue()));
            return result;
        }

        JudgeConfig judgeConfig = JSONUtil.toBean(
                judgeContext.getQuestion().getJudgeConfig(), JudgeConfig.class);
        if (memory > judgeConfig.getMemoryLimit() + additionalMemoryLimit(judgeContext)) {
            result.setMessage(JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED.getValue());
            return result;
        }
        if (time > judgeConfig.getTimeLimit() + additionalTimeLimit(judgeContext)) {
            result.setMessage(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue());
            return result;
        }

        return compareOutput(judgeContext, result);
    }

    /**
     * 子类仅在语言确实需要额外时间补偿时覆盖。
     */
    protected long additionalTimeLimit(JudgeContext judgeContext) {
        return 0L;
    }

    /**
     * 子类仅在语言运行时确实需要额外内存补偿时覆盖，单位为 KB。
     */
    protected long additionalMemoryLimit(JudgeContext judgeContext) {
        return 0L;
    }

    private JudgeInfoMessageEnum getExecutionFailure(Integer executeStatus) {
        if (Objects.equals(executeStatus, ExecuteStatusEnum.COMPILE_ERROR.getValue())) {
            return JudgeInfoMessageEnum.COMPILE_ERROR;
        }
        if (Objects.equals(executeStatus, ExecuteStatusEnum.RUNTIME_ERROR.getValue())) {
            return JudgeInfoMessageEnum.RUNTIME_ERROR;
        }
        if (Objects.equals(executeStatus, ExecuteStatusEnum.SYSTEM_ERROR.getValue())) {
            return JudgeInfoMessageEnum.SYSTEM_ERROR;
        }
        return null;
    }

    private JudgeInfo compareOutput(JudgeContext judgeContext, JudgeInfo result) {
        List<JudgeCase> judgeCases = judgeContext.getJudgeCaseList();
        List<String> outputList = Optional.ofNullable(judgeContext.getOutputList()).orElse(List.of());
        int comparableSize = Math.min(judgeCases.size(), outputList.size());
        for (int i = 0; i < comparableSize; i++) {
            JudgeCase judgeCase = judgeCases.get(i);
            if (!Objects.equals(judgeCase.getOutput(), outputList.get(i))) {
                result.setMessage(wrongAnswerMessage(i, judgeCase, outputList.get(i)));
                return result;
            }
        }

        if (outputList.size() < judgeCases.size()) {
            int failedIndex = outputList.size();
            result.setMessage(wrongAnswerMessage(failedIndex, judgeCases.get(failedIndex), NO_OUTPUT));
            return result;
        }
        if (outputList.size() > judgeCases.size()) {
            result.setMessage("Wrong Answer\n预期输出数量: " + judgeCases.size()
                    + "\n实际输出数量: " + outputList.size());
            return result;
        }

        result.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        return result;
    }

    private String wrongAnswerMessage(int index, JudgeCase judgeCase, String actualOutput) {
        return "Wrong Answer\n用例: " + (index + 1)
                + "\n输入:\n" + judgeCase.getInput()
                + "\n预期输出:\n" + judgeCase.getOutput()
                + "\n实际输出:\n" + actualOutput;
    }
}
