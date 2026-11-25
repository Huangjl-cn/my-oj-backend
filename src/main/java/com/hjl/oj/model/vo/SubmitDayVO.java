package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 做题日历里的一天
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDayVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日期，yyyy-MM-dd")
    private String date;

    @Schema(description = "这一天的提交次数")
    private Integer submitCount;

    @Schema(description = "其中通过的次数")
    private Integer acceptedCount;
}
