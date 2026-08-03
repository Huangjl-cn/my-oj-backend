package com.hjl.oj.judge;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.judge.strategy.DefaultJudgeStrategy;
import com.hjl.oj.judge.strategy.JudgeContext;
import com.hjl.oj.judge.strategy.JudgeStrategy;
import com.hjl.oj.judge.strategy.LanguageJudgeStrategy;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 判题管理：策略注册表 + 策略模式。
 * 构造时把每种语言映射到一个判题策略（默认策略兜底，特化策略覆盖），
 * 判题时按提交的语言查表，拿到对应策略执行，无需在调用处写 if/switch。
 */
@Service
public class JudgeManager {

    /**
     * 语言 -> 判题策略 的注册表（EnumMap 保证 key 是枚举且查表高效）
     */
    private final Map<QuestionSubmitLanguageEnum, JudgeStrategy> strategyRegistry;

    /**
     * 唯一构造器，Spring 自动注入（无需 @Autowired）。
     * 两个参数：
     * - defaultStrategy：默认策略 Bean（兜底所有语言）
     * - languageStrategies：容器里所有特化策略 Bean 的集合（如 Java 专用策略）
     * <p>
     * 注册过程分两步：
     * 1. 先把所有语言都指向默认策略（铺底）；
     * 2. 再用特化策略按语言覆盖对应条目（如 JAVA -> JavaLanguageJudgeStrategy）。
     * 若两个特化策略声明了同一语言，启动时直接抛异常（fail-fast）。
     */
    public JudgeManager(DefaultJudgeStrategy defaultStrategy,
                        List<LanguageJudgeStrategy> languageStrategies) {
        strategyRegistry = new EnumMap<>(QuestionSubmitLanguageEnum.class);
        // 第一步：所有语言默认都用默认策略
        for (QuestionSubmitLanguageEnum language : QuestionSubmitLanguageEnum.values()) {
            strategyRegistry.put(language, defaultStrategy);
        }
        // 第二步：特化策略覆盖对应语言；previous 不是默认策略说明该语言已被其他策略注册过
        for (LanguageJudgeStrategy strategy : languageStrategies) {
            JudgeStrategy previous = strategyRegistry.put(strategy.getLanguage(), strategy);
            if (previous != defaultStrategy) {
                throw new IllegalStateException("重复的判题策略: " + strategy.getLanguage().getValue());
            }
        }
    }

    /**
     * 从提交记录解析语言，并将判题上下文分发给对应策略
     *
     * @param judgeContext 判题上下文
     * @return 判题信息
     */
    JudgeInfo applyStrategy(JudgeContext judgeContext) {
        QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
        QuestionSubmitLanguageEnum language = QuestionSubmitLanguageEnum.getEnumByValue(
                questionSubmit.getLanguage());
        if (language == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的编程语言");
        }
        return resolveStrategy(language).evaluate(judgeContext);
    }

    /**
     * 查表获取策略；构造时所有语言都已铺底，所以不会返回 null
     */
    JudgeStrategy resolveStrategy(QuestionSubmitLanguageEnum language) {
        return strategyRegistry.get(language);
    }
}
