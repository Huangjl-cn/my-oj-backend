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
 * AI 陪练会话：每个用户在每道题下一行，记录题目级的状态（提示档位、方向、当前对话）
 */
@TableName(value = "companion_session")
@Data
public class CompanionSession implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long questionId;

    /**
     * 提示档位 0-5，5 表示看过答案
     */
    private Integer hintLevel;

    /**
     * 陪练判断的方向，见 HintTrackEnum；只给模型用，不展示给用户
     */
    private String hintTrack;

    /**
     * 当前对话，见 CompanionConversation
     */
    private Long activeConversationId;

    private Date createTime;

    private Date updateTime;
}
