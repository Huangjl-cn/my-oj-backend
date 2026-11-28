package com.hjl.oj.model.vo;

import com.hjl.oj.companion.trace.TraceToolCall;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 轨迹里的一步：一次模型调用或一次工具调用
 */
@Data
public class CompanionTraceStepVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "model 模型调用 / tool 工具调用")
    private String kind;

    @Schema(description = "模型名或工具名")
    private String name;

    @Schema(description = "工具参数（JSON）")
    @ToString.Exclude
    private String input;

    @Schema(description = "模型回答的正文 / 工具结果")
    @ToString.Exclude
    private String output;

    @Schema(description = "模型的思考过程")
    @ToString.Exclude
    private String thinking;

    @Schema(description = "模型发起的工具调用")
    private List<TraceToolCall> toolCalls;

    private Integer inputTokens;

    @Schema(description = "输入里命中缓存的 token")
    private Integer cachedTokens;

    private Integer outputTokens;

    private String finishReason;

    @Schema(description = "出错原因；工具调用被拒绝、参数不对等也算")
    private String error;

    @Schema(description = "开始时间；从记忆还原的没有")
    private Date startTime;

    @Schema(description = "耗时（毫秒）；从记忆还原的没有")
    private Integer durationMs;
}
