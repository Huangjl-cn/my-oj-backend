package com.hjl.oj.model.dto.user;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求体
 */
@Data
public class UserRegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3191241716373120793L;

    private String userAccount;

    @ToString.Exclude
    private String userPassword;

    @ToString.Exclude
    private String checkPassword;

    private String userName;
}
