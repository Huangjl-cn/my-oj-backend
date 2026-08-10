package com.hjl.oj.judge;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.CodeSandbox;
import com.hjl.oj.judge.codesandbox.CodeSandboxFactory;
import com.hjl.oj.judge.codesandbox.CodeSandboxProxy;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.hjl.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.judge.strategy.model.JudgeContext;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.ExecuteStatusEnum;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.service.JudgeCaseDataService;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;

    @Resource
    private JudgeCaseDataService judgeCaseDataService;

    @Resource
    private JudgeInputEncoder judgeInputEncoder;

    @Value("${codesandbox.type:example}")
    private String type;


    @Override
    public QuestionSubmit processSubmission(long questionSubmitId) {
        // 1）获取提交信息，并原子抢占判题任务
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        boolean claimed = questionSubmitService.updateStatusIfCurrent(
                questionSubmitId,
                QuestionSubmitStatusEnum.WAITING,
                QuestionSubmitStatusEnum.RUNNING,
                null);
        if (!claimed) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交已被其他判题任务处理");
        }

        try {
            return runJudgePipeline(questionSubmitId, questionSubmit);
        } catch (Exception e) {
            markFailed(questionSubmitId);
            throw e;
        }
    }

    private QuestionSubmit runJudgePipeline(long questionSubmitId, QuestionSubmit questionSubmit) {
        Question question = questionService.getById(questionSubmit.getQuestionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        // 2）调用沙箱，获取执行结果
        //使用工厂模式来获取代码沙箱的类型
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        //使用代理的模式来进行，进行日志输出等功能拓展
        codeSandbox = new CodeSandboxProxy(codeSandbox);
        //为代码执行请求设置参数
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();
        String judgeCaseStr = question.getJudgeCase();
        JudgeCaseConfig judgeCaseConfig = judgeCaseDataService.deserialize(judgeCaseStr);
        judgeCaseDataService.validate(judgeCaseConfig);
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .cases(judgeInputEncoder.encode(judgeCaseConfig))
                .build();
        //调用代码沙箱执行代码，获取结果
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        List<String> outputList = executeCodeResponse.getOutputList();
        JudgeContext judgeContext = new JudgeContext();
        // 执行状态码
        judgeContext.setExecuteStatus(executeCodeResponse.getStatus());
        judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
        judgeContext.setOutputList(outputList);
        judgeContext.setJudgeCaseConfig(judgeCaseConfig);
        judgeContext.setQuestion(question);
        judgeContext.setQuestionSubmit(questionSubmit);
        //根据语言属性来获取执行哪个判题策略
        JudgeInfo judgeInfo = judgeManager.applyStrategy(judgeContext);
        String judgeInfoJson = JSONUtil.toJsonStr(judgeInfo);
        if (!isCountableExecutionStatus(executeCodeResponse.getStatus())) {
            markFailed(questionSubmitId, judgeInfoJson);
            return questionSubmitService.getById(questionSubmitId);
        }
        boolean accepted = JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage());
        return questionSubmitService.completeSubmissionAndUpdateStats(
                questionSubmitId,
                questionSubmit.getQuestionId(),
                judgeInfoJson,
                accepted);
    }

    private void markFailed(long questionSubmitId) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
        markFailed(questionSubmitId, JSONUtil.toJsonStr(judgeInfo));
    }

    private void markFailed(long questionSubmitId, String judgeInfoJson) {
        try {
            boolean updated = questionSubmitService.updateStatusIfCurrent(
                    questionSubmitId,
                    QuestionSubmitStatusEnum.RUNNING,
                    QuestionSubmitStatusEnum.FAILED,
                    judgeInfoJson);
            if (!updated) {
                log.warn("判题失败状态未更新，questionSubmitId={}", questionSubmitId);
            }
        } catch (Exception updateException) {
            log.error("判题失败状态更新异常，questionSubmitId={}", questionSubmitId, updateException);
        }
    }

    private boolean isCountableExecutionStatus(Integer executeStatus) {
        return ExecuteStatusEnum.ACCEPTED.getValue().equals(executeStatus)
                || ExecuteStatusEnum.COMPILE_ERROR.getValue().equals(executeStatus)
                || ExecuteStatusEnum.RUNTIME_ERROR.getValue().equals(executeStatus);
    }
}
