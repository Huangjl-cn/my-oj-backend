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
import com.hjl.oj.judge.strategy.JudgeContext;
import com.hjl.oj.model.dto.question.JudgeCase;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.JudgeInfoMessageEnum;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;

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
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());//获取判题用例中的输入用例
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
        //调用代码沙箱执行代码，获取结果
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        List<String> outputList = executeCodeResponse.getOutputList();
        JudgeContext judgeContext = new JudgeContext();
        // 执行状态码
        judgeContext.setExecuteStatus(executeCodeResponse.getStatus());
        judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
        judgeContext.setInputList(inputList);
        judgeContext.setOutputList(outputList);
        judgeContext.setJudgeCaseList(judgeCaseList);
        judgeContext.setQuestion(question);
        judgeContext.setQuestionSubmit(questionSubmit);
        //根据语言属性来获取执行哪个判题策略
        JudgeInfo judgeInfo = judgeManager.applyStrategy(judgeContext);
        // 3）仅允许仍在判题中的任务进入成功终态
        boolean completed = questionSubmitService.updateStatusIfCurrent(
                questionSubmitId,
                QuestionSubmitStatusEnum.RUNNING,
                QuestionSubmitStatusEnum.SUCCEED,
                JSONUtil.toJsonStr(judgeInfo));
        if (!completed) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        return questionSubmitService.getById(questionSubmitId);
    }

    private void markFailed(long questionSubmitId) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
        try {
            boolean updated = questionSubmitService.updateStatusIfCurrent(
                    questionSubmitId,
                    QuestionSubmitStatusEnum.RUNNING,
                    QuestionSubmitStatusEnum.FAILED,
                    JSONUtil.toJsonStr(judgeInfo));
            if (!updated) {
                log.warn("判题失败状态未更新，questionSubmitId={}", questionSubmitId);
            }
        } catch (Exception updateException) {
            log.error("判题失败状态更新异常，questionSubmitId={}", questionSubmitId, updateException);
        }
    }
}
