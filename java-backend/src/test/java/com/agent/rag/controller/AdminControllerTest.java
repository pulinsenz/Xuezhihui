package com.agent.rag.controller;

import com.agent.rag.config.JwtProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员接口集成测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 覆盖：admin 用户管理全链路（列表/改角色/删除+强制下线）；
 * 普通 user 访问管理接口被拒；未登录被拦截。测试自清理。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class AdminControllerTest {

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
            jdbcTemplate.update("DELETE kd FROM knowledge_doc kd JOIN knowledge k ON kd.knowledgeId = k.id WHERE k.userId = ?", uid);
            jdbcTemplate.update("DELETE FROM knowledge WHERE userId = ?", uid);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        createdUserIds.clear();
    }

    private long registerUser(String account, String pass) throws Exception {
        String reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass, "checkPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long uid = objectMapper.readTree(reg).get("data").asLong();
        createdUserIds.add(uid);
        return uid;
    }

    private String login(String account, String pass) throws Exception {
        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = objectMapper.readTree(login).get("data").get("token").asText();
        createdTokens.add(token);
        return token;
    }

    /** 注册一个普通用户并提权为 admin，返回登录 token */
    private String createAdminAndLogin() throws Exception {
        String account = "admin_" + System.currentTimeMillis();
        String pass = "pass12345";
        long uid = registerUser(account, pass);
        jdbcTemplate.update("UPDATE user SET userRole='admin' WHERE id=?", uid);
        return login(account, pass);
    }

    private String createNormalUserAndLogin() throws Exception {
        String account = "test_" + System.currentTimeMillis();
        String pass = "pass12345";
        registerUser(account, pass);
        return login(account, pass);
    }

    @Test
    void admin_canManageUsers() throws Exception {
        String adminToken = createAdminAndLogin();

        // 用户列表（至少含自己）
        String list = mockMvc.perform(get("/admin/user/list").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode listNode = objectMapper.readTree(list);
        assertEquals(0, listNode.get("code").asInt());
        assertTrue(listNode.get("data").get("records").size() >= 1);

        // 改角色：普通用户 → admin，DB 实时生效
        String targetAccount = "test_" + System.currentTimeMillis();
        long targetId = registerUser(targetAccount, "pass12345");
        String roleResp = mockMvc.perform(put("/admin/user/{id}/role", targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userRole\":\"admin\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(roleResp).get("code").asInt());
        assertEquals("admin",
                jdbcTemplate.queryForObject("SELECT userRole FROM user WHERE id=?", String.class, targetId));

        // 删除普通用户：其 token 立即失效
        String victimAccount = "test_" + System.currentTimeMillis();
        long victimId = registerUser(victimAccount, "pass12345");
        String victimToken = login(victimAccount, "pass12345");
        // 删除前能访问 /me
        String meBefore = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + victimToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(meBefore).get("code").asInt());

        String delResp = mockMvc.perform(delete("/admin/user/{id}", victimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(delResp).get("code").asInt());
        // 删除后 token 访问 /me → 白名单已清、用户已删 → 401
        String meAfter = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + victimToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(meAfter).get("code").asInt());
    }

    @Test
    void normalUser_cannotAccessAdminApi() throws Exception {
        String userToken = createNormalUserAndLogin();
        String resp = mockMvc.perform(get("/admin/user/list").header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, objectMapper.readTree(resp).get("code").asInt());
    }

    @Test
    void admin_requiresLogin() throws Exception {
        String resp = mockMvc.perform(get("/admin/user/list"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(resp).get("code").asInt());
    }

    @Test
    void admin_canSeeAndRestoreDeletedUsers() throws Exception {
        String adminToken = createAdminAndLogin();
        String victimAccount = "test_" + System.currentTimeMillis();
        long victimId = registerUser(victimAccount, "pass12345");
        login(victimAccount, "pass12345");

        // 删除用户
        mockMvc.perform(delete("/admin/user/{id}", victimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();

        // 正常列表（deleted=0）看不到已删除用户
        String normalList = mockMvc.perform(get("/admin/user/list")
                        .param("deleted", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(objectMapper.readTree(normalList).get("data").get("records").toString().contains(victimAccount));

        // deleted=1 列表能看到已删除用户
        String deletedList = mockMvc.perform(get("/admin/user/list")
                        .param("deleted", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(objectMapper.readTree(deletedList).get("data").get("records").toString().contains(victimAccount));

        // 恢复用户
        String restore = mockMvc.perform(put("/admin/user/{id}/restore", victimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(restore).get("code").asInt());
        assertEquals("0",
                jdbcTemplate.queryForObject("SELECT isDelete FROM user WHERE id=?", String.class, victimId));

        // 恢复后 deleted=1 列表不再包含
        String deletedList2 = mockMvc.perform(get("/admin/user/list")
                        .param("deleted", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(objectMapper.readTree(deletedList2).get("data").get("records").toString().contains(victimAccount));
    }
}
