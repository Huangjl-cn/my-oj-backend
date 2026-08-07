package com.hjl.oj.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户角色选项。
 */
@Data
@AllArgsConstructor
public class UserRoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String text;

    private String value;
}
