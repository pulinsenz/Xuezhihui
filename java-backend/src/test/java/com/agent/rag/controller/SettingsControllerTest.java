package com.agent.rag.controller;

import com.agent.rag.config.JwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 用户设置接口测试（MockMvc + 真实 MySQL）
 *
 * @author pulinsenz
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtProperties jwtProperties;

    private final List<String> createdTokens = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdTokens.forEach(token ->
                stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token));
        createdTokens.clear();
        for (Long uid : createdUserIds) {
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        createdUserIds.clear();
    }

    private String registerAndLogin() throws Exception {
        String account = "test_" + System.currentTimeMillis();
        String pass = "pass12345";
        String reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RegisterTestSupport.registerBody(mockMvc, objectMapper, stringRedisTemplate, account, pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long uid = objectMapper.readTree(reg).get("data").asLong();
        createdUserIds.add(uid);
        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = objectMapper.readTree(login).get("data").get("token").asText();
        createdTokens.add(token);
        return token;
    }

    @Test
    void settings_requiresLogin() throws Exception {
        String body = mockMvc.perform(get("/settings"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void settings_getReturnsDefault() throws Exception {
        String token = registerAndLogin();
        String body = mockMvc.perform(get("/settings").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());
        assertEquals(1, objectMapper.readTree(body).get("data").get("defaultVectorize").asInt(), "默认应为默认入库");
    }

    @Test
    void settings_updateVectorizeDefault() throws Exception {
        String token = registerAndLogin();

        String body = mockMvc.perform(put("/settings/vectorize-default")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultVectorize\":0}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        String getBody = mockMvc.perform(get("/settings").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(getBody).get("data").get("defaultVectorize").asInt(), "更新后应生效");
    }

    @Test
    void settings_updateInvalidValue_rejected() throws Exception {
        String token = registerAndLogin();
        String body = mockMvc.perform(put("/settings/vectorize-default")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultVectorize\":2}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void settings_updateCollapseRefs() throws Exception {
        String token = registerAndLogin();

        String body = mockMvc.perform(put("/settings/collapse-refs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collapseRefs\":0}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        String getBody = mockMvc.perform(get("/settings").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(getBody).get("data").get("collapseRefs").asInt(), "更新后应生效");
    }
}
