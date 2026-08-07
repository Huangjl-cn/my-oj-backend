package com.hjl.oj.model.vo;

import com.hjl.oj.model.entity.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 管理员用户视图，不返回密码和第三方登录凭证。
 */
@Data
public class AdminUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String userAccount;

    private String userName;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    private Date createTime;

    private Date updateTime;

    public static AdminUserVO objToVo(User user) {
        if (user == null) {
            return null;
        }
        AdminUserVO adminUserVO = new AdminUserVO();
        BeanUtils.copyProperties(user, adminUserVO);
        return adminUserVO;
    }
}
