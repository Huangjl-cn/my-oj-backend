package com.hjl.oj.model.dto.questionsubmit;

import com.hjl.oj.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询题目提交
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionSubmitQueryRequest extends PageRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 题目 id
     */
    private Long questionId;
    /**
     * 用户 id
     */
    private Long userId;
    /**
     * 编程语言
     */
    private String language;
    /**
     * 代码状态
     */
    private Integer status;
}