package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目
 */
@TableName(value = "question")
@Data
public class Question implements Serializable {
    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 3478502196276717899L;
    /**
     * id
     * 用 ASSIGN_ID（雪花 id）而不是自增主键，防止他人按连续 id 遍历爬取题目
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 创建用户 id
     */
    private Long userId;
    /**
     * 标题
     */
    private String title;
    /**
     * 内容
     */
    private String content;
    /**
     * 标签列表（json 数组）
     */
    private String tags;
    /**
     * 题目答案
     */
    private String answer;
    /**
     * 题目提交数
     */
    private Integer submitNum;
    /**
     * 题目通过数
     */
    private Integer acceptedNum;
    /**
     * 判题配置（json 数组）
     */
    private String judgeConfig;
    /**
     * 判题用例（json 对象）
     */
    private String judgeCase;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

}