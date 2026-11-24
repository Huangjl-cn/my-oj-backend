package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 直接设置本题的提示等级：0 表示从头自己做，1-4 直接跳到某一级（第 5 级通过看答案进入）
 */
@Data
public class CompanionLevelRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long questionId;

    private Integer level;
}
