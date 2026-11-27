package com.hjl.oj.companion.trace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型在一次调用里发起的一个工具调用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型发起的一个工具调用")
public class TraceToolCall implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;

    @Schema(description = "参数（JSON）")
    @ToString.Exclude
    private String arguments;
}
