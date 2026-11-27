package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 笔记：可以挂在某道题上，也可以是自由笔记
 */
@TableName(value = "note")
@Data
public class Note implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /**
     * 关联的题目，自由笔记为空
     */
    private Long questionId;

    private String title;

    /**
     * 正文（Markdown），不打进日志
     */
    @ToString.Exclude
    private String content;

    /**
     * 版本号：每次保存加一，保存时对不上说明在别处改过了
     */
    private Integer version;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
