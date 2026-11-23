package com.hjl.oj.model.dto.user;

import com.hjl.oj.model.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LogInterceptor 会以 INFO 级别打印接口入参的 toString，含密码的请求对象和实体不能把密码带进日志。
 */
class PasswordToStringTest {

    private static final String SECRET = "plain-secret-123";

    @Test
    void loginRequestHidesPassword() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUserAccount("alice");
        request.setUserPassword(SECRET);
        assertHidden(request.toString());
    }

    @Test
    void registerRequestHidesPasswords() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserAccount("alice");
        request.setUserPassword(SECRET);
        request.setCheckPassword(SECRET);
        assertHidden(request.toString());
    }

    @Test
    void passwordUpdateRequestHidesPasswords() {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest();
        request.setOldPassword(SECRET);
        request.setNewPassword(SECRET);
        request.setCheckPassword(SECRET);
        assertFalse(request.toString().contains(SECRET), request.toString());
    }

    @Test
    void userEntityHidesPasswordDigest() {
        User user = new User();
        user.setUserAccount("alice");
        user.setUserPassword(SECRET);
        assertHidden(user.toString());
    }

    private static void assertHidden(String text) {
        assertFalse(text.contains(SECRET), text);
        assertTrue(text.contains("alice"), "其余字段应照常输出: " + text);
    }
}
