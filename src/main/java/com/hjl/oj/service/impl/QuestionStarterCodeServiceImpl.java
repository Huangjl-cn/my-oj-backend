package com.hjl.oj.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.mapper.QuestionStarterCodeMapper;
import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.QuestionStarterCodeSaveRequest;
import com.hjl.oj.model.entity.QuestionStarterCode;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.hjl.oj.service.QuestionStarterCodeService;
import com.hjl.oj.utils.QuestionStarterCodeDefaults;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 题目初始代码模板服务实现
 */
@Service
public class QuestionStarterCodeServiceImpl extends ServiceImpl<QuestionStarterCodeMapper, QuestionStarterCode>
        implements QuestionStarterCodeService {

    @Override
    public List<QuestionStarterCode> normalizeStarterCodes(List<QuestionStarterCodeSaveRequest> starterCodeList,
                                                           JudgeCaseConfig judgeCaseConfig) {
        if (starterCodeList == null) {
            ThrowUtils.throwIf(judgeCaseConfig == null, ErrorCode.PARAMS_ERROR,
                    "生成默认初始代码需要判题用例配置");
            return createDefaultStarterCodes(judgeCaseConfig);
        }
        ThrowUtils.throwIf(CollUtil.isEmpty(starterCodeList), ErrorCode.PARAMS_ERROR, "初始代码模板不能为空");

        Map<QuestionSubmitLanguageEnum, String> starterCodeMap = new HashMap<>();
        Set<QuestionSubmitLanguageEnum> languageSet = new HashSet<>();
        for (QuestionStarterCodeSaveRequest starterCodeRequest : starterCodeList) {
            ThrowUtils.throwIf(starterCodeRequest == null, ErrorCode.PARAMS_ERROR, "初始代码模板不能为空");
            QuestionSubmitLanguageEnum language = QuestionSubmitLanguageEnum
                    .getEnumByValue(starterCodeRequest.getLanguage());
            ThrowUtils.throwIf(language == null, ErrorCode.PARAMS_ERROR, "编程语言错误");
            ThrowUtils.throwIf(!languageSet.add(language), ErrorCode.PARAMS_ERROR, "编程语言重复");
            String starterCode = starterCodeRequest.getStarterCode();
            ThrowUtils.throwIf(StringUtils.isBlank(starterCode) && judgeCaseConfig == null,
                    ErrorCode.PARAMS_ERROR, "生成默认初始代码需要判题用例配置");
            starterCodeMap.put(language, StringUtils.isBlank(starterCode)
                    ? QuestionStarterCodeDefaults.getDefaultStarterCode(language, judgeCaseConfig)
                    : starterCode);
        }

        ThrowUtils.throwIf(languageSet.size() != QuestionSubmitLanguageEnum.values().length,
                ErrorCode.PARAMS_ERROR, "初始代码模板缺少支持语言");
        return buildStarterCodes(starterCodeMap, judgeCaseConfig);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceStarterCodes(long questionId, List<QuestionStarterCode> starterCodes) {
        ThrowUtils.throwIf(questionId <= 0 || CollUtil.isEmpty(starterCodes), ErrorCode.PARAMS_ERROR);
        baseMapper.delete(new LambdaQueryWrapper<QuestionStarterCode>()
                .eq(QuestionStarterCode::getQuestionId, questionId));
        for (QuestionStarterCode starterCode : starterCodes) {
            starterCode.setQuestionId(questionId);
            int inserted = baseMapper.insert(starterCode);
            ThrowUtils.throwIf(inserted != 1, ErrorCode.OPERATION_ERROR, "初始代码模板保存失败");
        }
    }

    @Override
    public QuestionStarterCode getByQuestionIdAndLanguage(long questionId, String language) {
        return this.getOne(new LambdaQueryWrapper<QuestionStarterCode>()
                .eq(QuestionStarterCode::getQuestionId, questionId)
                .eq(QuestionStarterCode::getLanguage, language));
    }

    @Override
    public List<QuestionStarterCode> listByQuestionId(long questionId) {
        return this.list(new LambdaQueryWrapper<QuestionStarterCode>()
                .eq(QuestionStarterCode::getQuestionId, questionId));
    }

    private List<QuestionStarterCode> createDefaultStarterCodes(JudgeCaseConfig judgeCaseConfig) {
        return buildStarterCodes(Map.of(), judgeCaseConfig);
    }

    private List<QuestionStarterCode> buildStarterCodes(Map<QuestionSubmitLanguageEnum, String> starterCodeMap,
                                                        JudgeCaseConfig judgeCaseConfig) {
        List<QuestionStarterCode> starterCodes = new ArrayList<>();
        for (QuestionSubmitLanguageEnum language : QuestionSubmitLanguageEnum.values()) {
            QuestionStarterCode starterCode = new QuestionStarterCode();
            starterCode.setLanguage(language.getValue());
            starterCode.setStarterCode(starterCodeMap.getOrDefault(language,
                    QuestionStarterCodeDefaults.getDefaultStarterCode(language, judgeCaseConfig)));
            starterCodes.add(starterCode);
        }
        return starterCodes;
    }
}
