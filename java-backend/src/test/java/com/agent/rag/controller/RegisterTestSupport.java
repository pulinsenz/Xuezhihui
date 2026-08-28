package com.agent.rag.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 控制器集成测试通用工具：注册前拉一道算术验证码并从 Redis 读答案，
 * 构造带 captchaId/captchaAnswer 的注册请求体。
 * <p>
 * 注册接口的自研验证码为一次性校验（答案存 Redis），测试必须走真实链路：
 * GET /auth/captcha 拿到 challengeId，再从 Redis 读答案填进请求体。
 * 否则直接 POST 注册会被 40000「验证码错误或已过期」拒绝。
 */
public final class RegisterTestSupport {

    private static final String CAPTCHA_KEY_PREFIX = "xzh:captcha:";

    private RegisterTestSupport() {
    }

    /**
     * 构造注册请求体（含真实一次性验证码）
     *
     * @param mockMvc   当前测试的 MockMvc
     * @param objectMapper 当前测试的 ObjectMapper
     * @param redis     当前测试注入的 StringRedisTemplate（读验证码答案）
     * @param account   账号
     * @param pass      密码（确认密码同值）
     */
    public static Map<String, String> registerBody(MockMvc mockMvc, ObjectMapper objectMapper,
                                                   StringRedisTemplate redis, String account, String pass) throws Exception {
        String captchaResp = mockMvc.perform(get("/auth/captcha"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode captcha = objectMapper.readTree(captchaResp);
        String challengeId = captcha.get("data").get("challengeId").asText();
        String answer = redis.opsForValue().get(CAPTCHA_KEY_PREFIX + challengeId);
        if (answer == null) {
            throw new IllegalStateException("验证码答案未写入 Redis: " + challengeId);
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("userAccount", account);
        body.put("userPassword", pass);
        body.put("checkPassword", pass);
        body.put("captchaId", challengeId);
        body.put("captchaAnswer", answer);
        return body;
    }
}
