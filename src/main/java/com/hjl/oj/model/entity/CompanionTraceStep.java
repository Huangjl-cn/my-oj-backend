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
 * 鸭鸭的轨迹：一轮里的一步，一次模型调用或一次工具调用
 */
@TableName(value = "companion_trace_step")
@Data
public class CompanionTraceStep implements Serializable {

    public static final String KIND_MODEL = "model";

    public static final String KIND_TOOL = "tool";

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long turnId;

    private Long conversationId;

    private String kind;

    private String name;

    /**
     * 工具参数（JSON）
     */
    @ToString.Exclude
    private String input;

    @ToString.Exclude
    private String output;

    @ToString.Exclude
    private String thinking;

    /**
     * 模型发起的工具调用（TraceToolCallVO 列表的 JSON）
     */
    @ToString.Exclude
    private String toolCalls;

    private Integer inputTokens;

    private Integer cachedTokens;

    private Integer outputTokens;

    private String finishReason;

    private String error;

    private Date startTime;

    private Integer durationMs;
}
