package com.hjl.oj.model.dto.questionsubmit;

import com.hjl.oj.constant.CommonConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交归档页查询请求（按题目聚合视图）
 * <p>先按判题结果筛选提交记录，再按题目聚合排序分页。
 */
@Data
public class QuestionSubmitGroupQueryRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小（1-20）
     */
    @Schema(description = "页面大小，范围 1-20", example = "10")
    private int pageSize = 10;

    /**
     * 判题结果筛选（all/accepted/failed/pending，不传或 all 不过滤）
     */
    @Schema(description = "判题结果筛选，可选 all/accepted/failed/pending，不传或 all 不过滤", example = "all")
    private String judgeResult;

    /**
     * 编程语言筛选（不传不过滤，筛选后仅按该语言的提交聚合）
     */
    @Schema(description = "编程语言筛选，不传不过滤；筛选后仅按该语言的提交聚合", example = "java")
    private String language;

    /**
     * 提交用户 id（不传不过滤）
     */
    @Schema(description = "提交用户 id 筛选，不传不过滤")
    private Long userId;

    /**
     * 提交者昵称（模糊搜索）
     */
    @Schema(description = "提交者昵称模糊搜索，不传不过滤；筛选后仅按该用户的提交聚合", example = "nelson")
    private String userName;

    /**
     * 排序字段（latestSubmitTime 最近提交时间/bestTime 最短判题耗时 ms/bestMemory 最小判题内存 KB，缺省 latestSubmitTime）
     */
    @Schema(description = "排序字段，可选 latestSubmitTime（最近提交时间，默认）/bestTime（最短判题耗时 ms）/bestMemory（最小判题内存 KB）",
            example = "latestSubmitTime")
    private String sortField;

    /**
     * 排序顺序（ascend/descend，缺省 descend）
     */
    @Schema(description = "排序顺序，可选 ascend/descend，缺省 descend", example = "descend")
    private String sortOrder = CommonConstant.SORT_ORDER_DESC;
}
