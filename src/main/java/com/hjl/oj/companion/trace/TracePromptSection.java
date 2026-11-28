package com.hjl.oj.companion.trace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发给模型的用户消息里的一段，比如「当前提示状态」「用户当前代码」
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发给模型的用户消息里的一段")
public class TracePromptSection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "段落标题，如「当前提示状态」；没有标题的开头部分为空")
    private String title;

    @ToString.Exclude
    private String text;
}
