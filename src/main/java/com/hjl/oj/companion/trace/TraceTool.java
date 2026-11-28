package com.hjl.oj.companion.trace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 轨迹里的一个工具：这一轮提供给模型的工具，名字、说明和参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提供给模型的一个工具")
public class TraceTool implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

    private List<Param> parameters;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TraceToolParam", description = "工具的一个参数")
    public static class Param implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String name;

        @Schema(description = "string / integer / number / boolean / array / object")
        private String type;

        private String description;

        private boolean required;
    }
}
