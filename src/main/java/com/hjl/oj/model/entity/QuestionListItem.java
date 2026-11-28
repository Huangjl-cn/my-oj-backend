package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题单里的一道题
 */
@TableName(value = "question_list_item")
@Data
public class QuestionListItem implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long listId;

    private Long questionId;

    /**
     * 在题单里的顺序，小的在前
     */
    private Integer sortOrder;

    /**
     * 加入题单的时间
     */
    private Date createTime;
}
