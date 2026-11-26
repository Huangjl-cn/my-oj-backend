package com.hjl.oj.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查接口：部署脚本和容器 healthcheck 依赖它，只能返回状态，不能暴露其他 actuator 端点或组件明细。
 * 测试环境不连数据库，所以关掉数据库检查（生产里数据库连不上时是 DOWN）。
 */
@SpringBootTest(properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsOnlyTheStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics",
                "/actuator/loggers", "/actuator/heapdump")) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}
