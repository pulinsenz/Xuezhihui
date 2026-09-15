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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 白名单集成测试（绑定 app.cors.allowed-origin-patterns 后生效性回归）
 * <p>
 * 回归背景：WebConfig 曾硬编码 localhost 两个源，application-prod.yml 的
 * https 生产域名从未被读取，跨域预检一律 403。本测试以独立属性集验证：
 * 白名单内源预检放行并回显 Allow-Origin，白名单外源拒绝。
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
        "cos.client.bucket=test-bucket",
        "app.cors.allowed-origin-patterns=https://xuezhihui.site,https://www.xuezhihui.site"
})
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private COSClient cosClient;

    @Test
    void preflight_whitelistedOrigin_allowedWithEchoedOrigin() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://xuezhihui.site")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://xuezhihui.site"));
    }

    @Test
    void preflight_whitelistedWwwOrigin_allowed() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://www.xuezhihui.site")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.xuezhihui.site"));
    }

    @Test
    void preflight_unknownOrigin_rejected403() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void simpleRequest_whitelistedOrigin_responseCarriesAllowOrigin() throws Exception {
        // 简单请求（非预检）：/auth/captcha 无需鉴权，响应头应带 Allow-Origin 供浏览器放行读取
        mockMvc.perform(get("/auth/captcha").header("Origin", "https://xuezhihui.site"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://xuezhihui.site"));
    }
}
