package com.hjl.oj.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjl.oj.annotation.AuthCheck;
import com.hjl.oj.common.BaseResponse;
import com.hjl.oj.common.DeleteRequest;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.common.ResultUtils;
import com.hjl.oj.constant.CommonConstant;
import com.hjl.oj.constant.UserConstant;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.model.dto.question.*;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitArchiveQueryRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitGroupQueryRequest;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.JudgeValueTypeEnum;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.hjl.oj.model.vo.*;
import com.hjl.oj.service.JudgeCaseDataService;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {


    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeCaseDataService judgeCaseDataService;

    // region 增删改查

    /**
     * 获取后端支持的编程语言
     */
    @GetMapping("/supported-languages")
    public BaseResponse<List<SupportedLanguageVO>> getSupportedLanguages() {
        List<SupportedLanguageVO> supportedLanguages = Stream.of(QuestionSubmitLanguageEnum.values())
                .map(language -> new SupportedLanguageVO(language.getText(), language.getValue()))
                .toList();
        return ResultUtils.success(supportedLanguages);
    }

    /**
     * 获取后端支持的判题值类型
     */
    @GetMapping("/supported-judge-types")  // HTTP GET请求映射，用于获取支持的判题类型
    public BaseResponse<List<SupportedJudgeTypeVO>> getSupportedJudgeTypes() {  // 方法声明，返回支持的判题类型列表的响应
        // 使用Stream API处理JudgeValueTypeEnum的所有枚举值，将其转换为SupportedJudgeTypeVO对象列表
        List<SupportedJudgeTypeVO> supportedJudgeTypes = Stream.of(JudgeValueTypeEnum.values())
                .map(type -> new SupportedJudgeTypeVO(  // 将枚举值映射为SupportedJudgeTypeVO对象
                        type.getValue(),      // 获取枚举值
                        type.getText(),       // 获取枚举文本描述
                        type.getCategory(),   // 获取枚举类别
                        type.getDimensions(), // 获取枚举维度
                        type.getElementType())) // 获取枚举元素类型
                .toList();  // 将流转换为列表
        return ResultUtils.success(supportedJudgeTypes);
    }

    /**
     * 创建
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        if (questionAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        JudgeCaseConfig judgeCase = questionAddRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(judgeCaseDataService.validateAndSerialize(judgeCase));
        }
        JudgeConfig judgeConfig = questionAddRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        questionService.validQuestion(question, true);
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        long newQuestionId = questionService.createQuestionWithStarterCodes(question,
                questionAddRequest.getStarterCodeList(), judgeCase);
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = questionService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        JudgeCaseConfig judgeCase = questionUpdateRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(judgeCaseDataService.validateAndSerialize(judgeCase));
        }
        JudgeConfig judgeConfig = questionUpdateRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        long id = questionUpdateRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = questionService.updateQuestionWithStarterCodes(question,
                questionUpdateRequest.getStarterCodeList(), judgeCase);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     */
    @GetMapping("/get")
    public BaseResponse<QuestionManageVO> getQuestionById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 不是本人或管理员，不能直接获取所有信息
        if (!question.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        QuestionManageVO questionManageVO = new QuestionManageVO();
        BeanUtils.copyProperties(question, questionManageVO);
        questionManageVO.setTags(JSONUtil.toList(question.getTags(), String.class));
        questionManageVO.setJudgeConfig(JSONUtil.toBean(question.getJudgeConfig(), JudgeConfig.class));
        questionManageVO.setJudgeCase(judgeCaseDataService.deserialize(question.getJudgeCase()));
        return ResultUtils.success(questionManageVO);
    }

    /**
     * 根据 id 获取（脱敏）
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 查询题解，登录用户均可访问。
     */
    @GetMapping("/solution")
    public BaseResponse<QuestionSolutionVO> getQuestionSolution(@RequestParam long questionId,
                                                                HttpServletRequest request) {
        if (questionId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        userService.getLoginUser(request);
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        QuestionSolutionVO solutionVO = new QuestionSolutionVO();
        solutionVO.setQuestionId(question.getId());
        solutionVO.setAnswer(question.getAnswer());
        return ResultUtils.success(solutionVO);
    }

    /**
     * 获取题目指定语言的初始代码模板
     */
    @GetMapping("/starter-code")
    public BaseResponse<QuestionStarterCodeVO> getQuestionStarterCode(@RequestParam long questionId,
                                                                      @RequestParam String language) {
        return ResultUtils.success(questionService.getQuestionStarterCodeVO(questionId, language));
    }

    /**
     * 获取题目全部初始代码模板，用于编辑题目时回填
     */
    @GetMapping("/starter-code/list")
    public BaseResponse<List<QuestionStarterCodeVO>> listQuestionStarterCode(@RequestParam long questionId) {
        return ResultUtils.success(questionService.listQuestionStarterCodeVO(questionId));
    }

    /**
     * 分页获取列表（仅管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前用户创建的资源列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑（用户）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        JudgeCaseConfig judgeCase = questionEditRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(judgeCaseDataService.validateAndSerialize(judgeCase));
        }
        JudgeConfig judgeConfig = questionEditRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        long id = questionEditRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionService.updateQuestionWithStarterCodes(question,
                questionEditRequest.getStarterCodeList(), judgeCase);
        return ResultUtils.success(result);
    }

    /**
     * 提交题目
     *
     * @param questionSubmitAddRequest 题目提交请求
     * @return 提交记录的 id
     */
    @PostMapping("/question_submit/do")
    public BaseResponse<Long> doQuestionSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest,
                                               HttpServletRequest request) {
        if (questionSubmitAddRequest == null || questionSubmitAddRequest.getQuestionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能提交题目
        final User loginUser = userService.getLoginUser(request);
        long questionSubmitId = questionSubmitService.doQuestionSubmit(questionSubmitAddRequest, loginUser);
        return ResultUtils.success(questionSubmitId);
    }

    /**
     * 分页获取提交归档列表（全部提交视图）
     * <p>支持按判题结果（judgeResult）筛选与按提交时间/判题耗时/判题内存排序，缺省按提交时间倒序；
     * 提交代码对所有登录用户可见（产品决策：公开代码促进学习）。
     *
     * @param archiveQueryRequest 提交归档查询请求
     * @return 题目提交归档信息
     */
    @PostMapping("/question_submit/list/page")
    public BaseResponse<Page<QuestionSubmitVO>> listQuestionSubmitByPage(@RequestBody QuestionSubmitArchiveQueryRequest archiveQueryRequest,
                                                                         HttpServletRequest request) {
        // 登录才能浏览提交归档
        final User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(questionSubmitService.getQuestionSubmitArchiveVOPage(archiveQueryRequest, loginUser));
    }

    /**
     * 分页获取按题目聚合的提交归档（按题目视图）
     * <p>先按判题结果筛选提交记录，再按题目聚合排序分页；total 为题目数量，
     * 每题返回筛选范围内最近一次结果、提交代码与最佳耗时/内存。
     *
     * @param groupQueryRequest 按题目聚合查询请求
     * @return 按题目聚合的提交归档分页
     */
    @PostMapping("/question_submit/group/page")
    public BaseResponse<Page<QuestionSubmitGroupVO>> listQuestionSubmitGroupByPage(@RequestBody QuestionSubmitGroupQueryRequest groupQueryRequest,
                                                                                   HttpServletRequest request) {
        // 登录才能浏览提交归档
        final User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(questionSubmitService.getQuestionSubmitGroupVOPage(groupQueryRequest, loginUser));
    }

    /**
     * 分页获取当前用户在指定题目下的提交摘要
     */
    @PostMapping("/question_submit/my/list/page")
    public BaseResponse<Page<QuestionSubmitSummaryVO>> listMyQuestionSubmitByPage(
            @RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest,
            HttpServletRequest request) {
        if (questionSubmitQueryRequest == null
                || questionSubmitQueryRequest.getQuestionId() == null
                || questionSubmitQueryRequest.getQuestionId() <= 0
                || questionSubmitQueryRequest.getCurrent() <= 0
                || questionSubmitQueryRequest.getPageSize() <= 0
                || questionSubmitQueryRequest.getPageSize() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        questionSubmitQueryRequest.setUserId(loginUser.getId());
        questionSubmitQueryRequest.setSortField("createTime");
        questionSubmitQueryRequest.setSortOrder(CommonConstant.SORT_ORDER_DESC);
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(
                new Page<>(questionSubmitQueryRequest.getCurrent(), questionSubmitQueryRequest.getPageSize()),
                questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));
        return ResultUtils.success(questionSubmitService.getQuestionSubmitSummaryVOPage(questionSubmitPage));
    }

    /**
     * 获取当前用户的某次提交详情
     */
    @GetMapping("/question_submit/my/get")
    public BaseResponse<QuestionSubmitVO> getMyQuestionSubmitById(@RequestParam long id,
                                                                  HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        QuestionSubmit questionSubmit = questionSubmitService.getByIdAndUserId(id, loginUser.getId());
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVO(questionSubmit, loginUser));
    }

    /**
     * 根据题目提交 id 获取详情（登录即可查看任意提交，提交代码对所有登录用户可见）
     */
    @GetMapping("/question_submit/get")
    public BaseResponse<QuestionSubmit> getQuestionSubmitById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionSubmit questionSubmit = questionSubmitService.getById(id);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 登录即可查看（产品决策：公开代码促进学习），仅需校验登录态
        userService.getLoginUser(request);
        return ResultUtils.success(questionSubmit);
    }

    /**
     * 获取提交的指标排名数据（同题同语言对比）
     * <p>对比人群：同一道题、同语言、通过（Accepted）且指标有效的提交（含本人若已通过）；
     * 耗时/内存越小越好，返回人群总数与指标大于等于本次提交（含本人与持平）的数量，比例由前端计算展示。
     * 首次通过提交为 1/1（即超过 100%），只要本人有指标就必有数据。
     *
     * @param id 提交 id
     * @return 排名统计数据（本人无有效指标时对应计数字段为 null）
     */
    @GetMapping("/question_submit/rank")
    public BaseResponse<QuestionSubmitRankVO> getQuestionSubmitRank(@RequestParam long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录即可查看
        userService.getLoginUser(request);
        return ResultUtils.success(questionSubmitService.getQuestionSubmitRank(id));
    }

    /**
     * 获取提交的排队状态（判题削峰：排队人数展示）
     * <p>待判题时返回前方排队人数与队列总长，队列按提交顺序派发，人数与实际判题顺序一致；
     * 非待判题状态前方人数为 0，前端收到 2/3 状态可停止轮询。
     *
     * @param id 提交 id
     * @return 排队状态
     */
    @GetMapping("/question_submit/queue/status")
    public BaseResponse<QuestionSubmitQueueStatusVO> getQuestionSubmitQueueStatus(@RequestParam long id,
                                                                                  HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录即可查看（与提交浏览一致）
        userService.getLoginUser(request);
        return ResultUtils.success(questionSubmitService.getQuestionSubmitQueueStatus(id));
    }
}
