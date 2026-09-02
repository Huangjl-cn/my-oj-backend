package com.hjl.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitGroupRow;
import com.hjl.oj.model.dto.questionsubmit.QuestionSubmitRankStatsRow;
import com.hjl.oj.model.entity.QuestionSubmit;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author hjl15
 * @description 针对表【question_submit(题目提交)】的数据库操作Mapper
 * @createDate 2025-12-18 18:55:53
 * @Entity com.hjl.oj.mapper.QuestionSubmitMapper
 */
public interface QuestionSubmitMapper extends BaseMapper<QuestionSubmit> {

    /**
     * 分页获取按题目聚合的提交归档行（rn=1 即每题筛选范围内最近一次提交）
     * <p>judgeResultSql / orderSql 由服务端根据枚举白名单生成，禁止拼接客户端输入；
     * language / userId / userIds 为客户端输入，经 #{} 预编译绑定。
     *
     * @param page           分页参数（需关闭自动 count）
     * @param judgeResultSql 判题结果筛选片段（前导 AND，可为空串）
     * @param language       编程语言筛选（空不过滤）
     * @param userId         提交用户 id 筛选（空不过滤）
     * @param userIds        提交者昵称解析出的用户 id 集合（空集合不过滤，匹配不到用户时由服务层短路返回空页）
     * @param orderSql       排序片段（ORDER BY 开头）
     */
    IPage<QuestionSubmitGroupRow> selectQuestionGroupPage(IPage<QuestionSubmitGroupRow> page,
                                                          @Param("judgeResultSql") String judgeResultSql,
                                                          @Param("language") String language,
                                                          @Param("userId") Long userId,
                                                          @Param("userIds") List<Long> userIds,
                                                          @Param("orderSql") String orderSql);

    /**
     * 统计有符合条件提交的题目数量（与 selectQuestionGroupPage 的筛选和联表保持一致）
     *
     * @param judgeResultSql 判题结果筛选片段（前导 AND，可为空串）
     * @param language       编程语言筛选（空不过滤）
     * @param userId         提交用户 id 筛选（空不过滤）
     * @param userIds        提交者昵称解析出的用户 id 集合（空集合不过滤）
     */
    Long countQuestionGroup(@Param("judgeResultSql") String judgeResultSql,
                            @Param("language") String language,
                            @Param("userId") Long userId,
                            @Param("userIds") List<Long> userIds);

    /**
     * 统计同题同语言、通过（Accepted）且指标有效（含本人若已通过）的提交中，
     * 耗时/内存大于等于目标提交（含本人与持平）的数量与人群总数。
     * <p>"超过"按不快于本次提交计，因此首次提交为 1/1（即超过 100%）；time / memory 允许为 null。
     *
     * @param acceptedSql Accepted 筛选片段（服务端枚举常量，来自 JudgeResultEnum）
     * @param questionId  目标提交的题目 id
     * @param language    目标提交的编程语言
     * @param time        目标提交耗时（ms），可为 null
     * @param memory      目标提交内存（KB），可为 null
     */
    QuestionSubmitRankStatsRow selectQuestionLanguageRankStats(@Param("acceptedSql") String acceptedSql,
                                                               @Param("questionId") long questionId,
                                                               @Param("language") String language,
                                                               @Param("time") Long time,
                                                               @Param("memory") Long memory);
}




