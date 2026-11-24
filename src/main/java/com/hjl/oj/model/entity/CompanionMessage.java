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
 * AI 陪练消息：给前端展示的对话历史
 */
@TableName(value = "companion_message")
@Data
public class CompanionMessage implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long questionId;

    /**
     * 见 CompanionRoleEnum
     */
    private String role;

    /**
     * 见 CompanionMessageTypeEnum
     */
    private String type;

    private Integer hintLevel;

    private String hintTrack;

    /**
     * 见 CompanionMoodEnum
     */
    private String mood;

    private Integer moodIntensity;

    private Long submissionId;

    private String content;

    /**
     * 代码修改建议（CodeEditProposal 的 JSON）
     */
    private String codeEdits;

    private Date createTime;
}
