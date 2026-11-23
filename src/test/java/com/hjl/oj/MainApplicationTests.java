package com.hjl.oj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 主类测试
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class MainApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void getBytesTest() {
        String s = "abc黄";
        System.out.println(Arrays.toString(s.getBytes(StandardCharsets.UTF_16LE)));
    }
}
