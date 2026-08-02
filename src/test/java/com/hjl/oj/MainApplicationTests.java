package com.hjl.oj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 主类测试
 */
@SpringBootTest
class MainApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void getBytesTest(){
        String s ="abc黄";
        System.out.println(Arrays.toString(s.getBytes(StandardCharsets.UTF_16LE)));
    }
}
