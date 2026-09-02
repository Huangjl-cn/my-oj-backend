package com.hjl.oj.model.dto.questionsubmit;

import com.hjl.oj.constant.CommonConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交归档页查询请求（全部提交视图）
 * <p>不继承 PageRequest：PageRequest 的 sortOrder 默认升序，与本接口"缺省降序"的契约冲突。
 */
@Data
public class QuestionSubmitArchiveQueryRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 提交用户 id
     */
    private Long userId;

    /**
     * 提交者昵称（模糊搜索）
     */
    @Schema(description = "提交者昵称模糊搜索，不传不过滤", example = "nelson")
    private String userName;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 判题状态（0-待判题，1-判题中，2-成功，3-失败）
     */
    private Integer status;

    /**
     * 判题结果筛选（all/accepted/failed/pending，不传或 all 不过滤）
     */
    @Schema(description = "判题结果筛选，可选 all/accepted/failed/pending，不传或 all 不过滤", example = "all")
    private String judgeResult;

    /**
     * 排序字段（createTime 提交时间/judgeTime 判题耗时 ms/judgeMemory 判题内存 KB，缺省 createTime）
     */
    @Schema(description = "排序字段，可选 createTime（提交时间，默认）/judgeTime（判题耗时 ms）/judgeMemory（判题内存 KB）",
            example = "createTime")
    private String sortField;

    /**
     * 排序顺序（ascend/descend，缺省 descend）
     */
    @Schema(description = "排序顺序，可选 ascend/descend，缺省 descend", example = "descend")
    private String sortOrder = CommonConstant.SORT_ORDER_DESC;
}
