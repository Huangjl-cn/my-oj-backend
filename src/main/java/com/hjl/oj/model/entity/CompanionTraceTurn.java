package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 鸭鸭的轨迹：一轮（一次聊天、提示、看答案、提交后反应、整理记忆）
 */
@TableName(value = "companion_trace_turn")
@Data
public class CompanionTraceTurn implements Serializable {

    public static final String STATUS_RUNNING = "running";

    public static final String STATUS_OK = "ok";

    public static final String STATUS_ERROR = "error";

    public static final String STATUS_FALLBACK = "fallback";

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long questionId;

    private Long conversationId;

    /**
     * 见 CompanionTraceType
     */
    private String type;

    private String status;

    @ToString.Exclude
    private String userText;

    @ToString.Exclude
    private String userPrompt;

    /**
     * 和这段对话上一轮相同时为空
     */
    @ToString.Exclude
    private String systemPrompt;

    /**
     * 工具清单（TraceToolVO 列表的 JSON），和上一轮相同时为空
     */
    @ToString.Exclude
    private String tools;

    private Long messageId;

    private String error;

    private Date startTime;

    private Date endTime;
}
