package com.hjl.oj.model.vo;

import com.hjl.oj.companion.edit.CodeEditProposal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 一条陪练消息（对话历史）
 */
@Data
public class CompanionMessageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "user 用户 / assistant 陪练")
    private String role;

    @Schema(description = "chat 聊天 / hint 提示 / reaction 提交后反应 / solution 参考解法 / compaction 记忆整理")
    private String type;

    @Schema(description = "提示等级（提示、解法）")
    private Integer hintLevel;

    @Schema(description = "提示方向 idea / bug")
    private String hintTrack;

    @Schema(description = "陪练心情（提交后反应）")
    private String mood;

    @Schema(description = "心情强度 1-3")
    private Integer moodIntensity;

    @Schema(description = "触发反应的提交 id")
    private Long submissionId;

    private String content;

    @Schema(description = "这条消息附带的代码修改建议，没有为 null")
    private CodeEditProposal codeEdits;

    private Date createTime;
}
