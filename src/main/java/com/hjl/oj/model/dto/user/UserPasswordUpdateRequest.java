package com.hjl.oj.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改个人密码请求。
 */
@Data
public class UserPasswordUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原密码。
     */
    private String oldPassword;

    /**
     * 新密码。
     */
    private String newPassword;

    /**
     * 确认新密码。
     */
    private String checkPassword;
}
