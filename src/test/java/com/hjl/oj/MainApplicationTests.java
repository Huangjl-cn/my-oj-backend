package com.hjl.oj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文装配冒烟测试：所有 Bean 能正常创建（不连数据库、不启动判题调度器）。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class MainApplicationTests {

    @Test
    void contextLoads() {
    }
}
