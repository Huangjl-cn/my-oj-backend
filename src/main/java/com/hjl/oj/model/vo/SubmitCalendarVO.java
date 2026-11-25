package com.hjl.oj.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 当前用户的做题日历：最近一段时间每天的提交次数，以及全部的提交统计
 */
@Data
public class SubmitCalendarVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "有提交的日子，按日期从早到晚；没提交的日子不在其中")
    private List<SubmitDayVO> days;

    @Schema(description = "统计了多少天（含今天）")
    private Integer rangeDays;

    @Schema(description = "全部提交次数")
    private Integer totalSubmits;

    @Schema(description = "全部通过的提交次数")
    private Integer acceptedSubmits;
}
