package com.hjl.oj.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.constant.CommonConstant;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.mapper.QuestionMapper;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.QuestionQueryRequest;
import com.hjl.oj.model.dto.question.QuestionStarterCodeSaveRequest;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionStarterCode;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.hjl.oj.model.vo.QuestionStarterCodeVO;
import com.hjl.oj.model.vo.QuestionVO;
import com.hjl.oj.model.vo.UserVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionStarterCodeService;
import com.hjl.oj.service.UserService;
import com.hjl.oj.utils.SqlUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    @Resource
    private UserService userService;

    @Resource
    private QuestionStarterCodeService questionStarterCodeService;

    /**
     * 判断传入的用例参数是否合法
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        //安装插件可快速生成
        String title = question.getTitle();
        String content = question.getContent();
        String tags = question.getTags();
        String answer = question.getAnswer();
        String judgeConfig = question.getJudgeConfig();
        String judgeCase = question.getJudgeCase();

        // 创建时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, tags), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(StringUtils.isBlank(judgeCase),
                    ErrorCode.PARAMS_ERROR, "判题用例不能为空");
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StringUtils.isNotBlank(answer) && answer.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长");
        }
        if (StringUtils.isNotBlank(judgeConfig) && judgeConfig.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判断配置过长");
        }
        if (StringUtils.isNotBlank(judgeCase) && judgeCase.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判断用例过长");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createQuestionWithStarterCodes(Question question,
                                               List<QuestionStarterCodeSaveRequest> starterCodeList,
                                               JudgeCaseConfig judgeCaseConfig) {
        List<QuestionStarterCode> starterCodes = questionStarterCodeService
                .normalizeStarterCodes(starterCodeList, judgeCaseConfig);
        boolean saved = this.save(question);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "题目保存失败");
        questionStarterCodeService.replaceStarterCodes(question.getId(), starterCodes);
        return question.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateQuestionWithStarterCodes(Question question,
                                                  List<QuestionStarterCodeSaveRequest> starterCodeList,
                                                  JudgeCaseConfig judgeCaseConfig) {
        List<QuestionStarterCode> starterCodes = null;
        if (starterCodeList != null) {
            starterCodes = questionStarterCodeService.normalizeStarterCodes(starterCodeList, judgeCaseConfig);
        }
        boolean hasQuestionUpdates = ObjectUtils.anyNotNull(
                question.getTitle(),
                question.getContent(),
                question.getTags(),
                question.getAnswer(),
                question.getJudgeConfig(),
                question.getJudgeCase());
        ThrowUtils.throwIf(!hasQuestionUpdates && starterCodes == null,
                ErrorCode.PARAMS_ERROR, "没有需要更新的内容");
        if (hasQuestionUpdates) {
            boolean updated = this.updateById(question);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "题目更新失败");
        }
        if (starterCodes != null) {
            questionStarterCodeService.replaceStarterCodes(question.getId(), starterCodes);
        }
        return true;
    }

    @Override
    public boolean incrementJudgeCount(long questionId, boolean accepted) {
        if (questionId <= 0) {
            return false;
        }
        LambdaUpdateWrapper<Question> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Question::getId, questionId)
                .eq(Question::getIsDelete, 0)
                .setSql(accepted
                        ? "submitNum = submitNum + 1, acceptedNum = acceptedNum + 1, updateTime = updateTime"
                        : "submitNum = submitNum + 1, updateTime = updateTime");
        return this.update(updateWrapper);
    }

    @Override
    public QuestionStarterCodeVO getQuestionStarterCodeVO(long questionId, String language) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(QuestionSubmitLanguageEnum.getEnumByValue(language) == null,
                ErrorCode.PARAMS_ERROR, "编程语言错误");
        ThrowUtils.throwIf(this.getById(questionId) == null, ErrorCode.NOT_FOUND_ERROR);
        QuestionStarterCode starterCode = questionStarterCodeService
                .getByQuestionIdAndLanguage(questionId, language);
        ThrowUtils.throwIf(starterCode == null, ErrorCode.NOT_FOUND_ERROR, "初始代码模板不存在");
        return QuestionStarterCodeVO.objToVo(starterCode);
    }

    @Override
    public List<QuestionStarterCodeVO> listQuestionStarterCodeVO(long questionId) {
        ThrowUtils.throwIf(questionId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(this.getById(questionId) == null, ErrorCode.NOT_FOUND_ERROR);
        Map<String, QuestionStarterCode> starterCodeMap = questionStarterCodeService.listByQuestionId(questionId)
                .stream()
                .collect(Collectors.toMap(QuestionStarterCode::getLanguage, Function.identity()));
        ThrowUtils.throwIf(starterCodeMap.size() != QuestionSubmitLanguageEnum.values().length,
                ErrorCode.NOT_FOUND_ERROR, "初始代码模板不完整");
        return Stream.of(QuestionSubmitLanguageEnum.values())
                .map(language -> starterCodeMap.get(language.getValue()))
                .map(starterCode -> {
                    ThrowUtils.throwIf(starterCode == null, ErrorCode.NOT_FOUND_ERROR, "初始代码模板不完整");
                    return QuestionStarterCodeVO.objToVo(starterCode);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取查询包装类：前端根据用户可能会用到哪些字段查询，传递一个请求对象，返回mybatis框架支持的查询QueryWrapper类
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        //考虑用户会用哪些字段来查询
        Long id = questionQueryRequest.getId();
        Long userId = questionQueryRequest.getUserId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tags = questionQueryRequest.getTags();
        String answer = questionQueryRequest.getAnswer();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 用于获取实体的封装类
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        QuestionVO questionVO = QuestionVO.objToVo(question);
        //关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUserVO(userVO);
        return questionVO;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, User> userIdUserMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // 填充信息
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            QuestionVO questionVO = QuestionVO.objToVo(question);
            Long userId = question.getUserId();
            User user = null;
            if (userIdUserMap.containsKey(userId)) {
                user = userIdUserMap.get(userId);
            }
            questionVO.setUserVO(userService.getUserVO(user));
            return questionVO;
        }).collect(Collectors.toList());
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

}




