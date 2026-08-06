package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目多语言初始代码模板
 */
@TableName(value = "question_starter_code")
@Data
public class QuestionStarterCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 题目 id
     */
    private Long questionId;
    /**
     * 编程语言
     */
    private String language;
    /**
     * 编辑器初始代码模板
     */
    private String starterCode;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
}
