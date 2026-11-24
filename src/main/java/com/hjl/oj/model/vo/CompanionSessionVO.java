package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 进入题目时拉取的陪练会话
 */
@Data
public class CompanionSessionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    @Schema(description = "已解锁的提示等级 0-5")
    private Integer hintLevel;

    @Schema(description = "实际生效的等级：有问必答模式下为 5")
    private Integer effectiveLevel;

    @Schema(description = "当前方向 idea 找思路 / bug 找 bug")
    private String hintTrack;

    @Schema(description = "提示模式 guided / direct")
    private String hintMode;

    @Schema(description = "两条线各自的步骤名称和说明，key 为 idea / bug")
    private Map<String, List<HintStepVO>> hintSteps;

    @Schema(description = "能否调用模型：没配 Key 时为 false，前端引导用户去配置")
    private Boolean canCallModel;

    @Schema(description = "是否为模拟模式")
    private Boolean mockMode;

    @Schema(description = "陪练的名字")
    private String personaName;

    private ContextUsageVO contextUsage;

    @Schema(description = "最近的消息，按时间正序")
    private List<CompanionMessageVO> messages;

    @Schema(description = "是否还有更早的消息")
    private Boolean hasMoreMessages;
}
