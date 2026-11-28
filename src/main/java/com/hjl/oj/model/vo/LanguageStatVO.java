package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用一种语言的提交情况
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LanguageStatVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "语言标识，如 java")
    private String language;

    @Schema(description = "语言的显示名，如 Java 25；已不再支持的语言就是标识本身")
    private String languageName;

    @Schema(description = "提交次数")
    private Integer submitCount;

    @Schema(description = "通过的提交次数")
    private Integer acceptedCount;

    @Schema(description = "用这种语言通过过的题目数")
    private Integer solvedCount;
}
