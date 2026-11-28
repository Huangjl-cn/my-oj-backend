package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题单（收藏、自建、官方）
 */
@TableName(value = "question_list")
@Data
public class QuestionList implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建人
     */
    private Long userId;

    private String title;

    private String description;

    /**
     * 见 QuestionListTypeEnum
     */
    private String type;

    /**
     * 收藏题单为 favorite:&lt;userId&gt;（唯一索引保证每人一个），其他为空
     */
    private String uniqueKey;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
