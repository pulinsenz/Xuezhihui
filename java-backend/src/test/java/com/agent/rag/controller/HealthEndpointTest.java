package com.agent.rag.controller;

import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查端点集成测试（/api/actuator/health）
 * <p>
 * 回归背景：健康端点此前不存在，未知路径被 LoginInterceptor 拦成业务 40100「未登录」，
 * 外部探测无法区分"服务健康"与"路径错误"。放行 /actuator/** 后：
 * 无凭证 GET 应返回 200 + UP（聚合 MySQL/Redis 指标，测试环境为真实连接）。
 *
 * @author pulinsenz
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "cos.client.host=https://test.cos.myqcloud.com",
        "cos.client.secret-id=test-secret-id",
        "cos.client.secret-key=test-secret-key",
        "cos.client.region=ap-guangzhou",
        "cos.client.bucket=test-bucket"
})
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private COSClient cosClient;

    @Test
    void health_noAuth_returns200WithUpStatus() throws Exception {
        // 无 Authorization 头直接访问：回归点是放行（旧版会被拦成 40100）
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void health_invalidToken_stillAccessible() throws Exception {
        // 即便携带伪造 token 也不应走登录校验逻辑（探针不会带凭证）
        mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
