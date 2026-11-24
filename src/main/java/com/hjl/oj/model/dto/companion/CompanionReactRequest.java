package com.hjl.oj.model.dto.companion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 判题结束后请求陪练的反应
 */
@Data
public class CompanionReactRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long submissionId;
}
