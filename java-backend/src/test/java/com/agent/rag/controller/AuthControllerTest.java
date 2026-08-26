package com.agent.rag.controller;

import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.req.UpdateProfileRequest;
import com.agent.rag.entity.User;
import com.agent.rag.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口集成测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 使用唯一账号，测试后自动清理数据库行和 Redis 白名单。
 * 头像上传走 COS：Mock 掉 SDK 客户端避免真实网络/密钥，CosProperties 用测试值注入。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "cos.client.host=https://test.cos.myqcloud.com",
        "cos.client.secret-id=test-secret-id",
        "cos.client.secret-key=test-secret-key",
        "cos.client.region=ap-guangzhou",
        "cos.client.bucket=test-bucket"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtProperties jwtProperties;
    @MockBean
    private COSClient cosClient;

    /** 本测试产生的 token，tearDown 时逐个清除白名单 */
    private final List<String> createdTokens = new ArrayList<>();
    private String testAccount;
    private final String testPassword = "pass12345";

    @BeforeEach
    void setUp() {
        testAccount = "test_" + System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        // 硬删除测试用户（逻辑删除会残留物理行）
        jdbcTemplate.update("DELETE FROM user WHERE userAccount LIKE 'test_%'");
        // 清理本测试产生的 Redis 白名单
        createdTokens.forEach(token ->
                stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token));
        createdTokens.clear();
    }

    private String postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void register_login_me_logout_fullFlow() throws Exception {
        // ---- 注册 ----
        RegisterRequest register = new RegisterRequest();
        register.setUserAccount(testAccount);
        register.setUserPassword(testPassword);
        register.setCheckPassword(testPassword);
        JsonNode regNode = objectMapper.readTree(postJson("/auth/register", register));
        assertEquals(0, regNode.get("code").asInt(), "注册应成功");
        assertTrue(regNode.get("data").asLong() > 0, "应返回 userId");

        // ---- 重复注册：账号已存在 ----
        JsonNode dupNode = objectMapper.readTree(postJson("/auth/register", register));
        assertEquals(40001, dupNode.get("code").asInt());

        // ---- 登录 ----
        LoginRequest login = new LoginRequest();
        login.setUserAccount(testAccount);
        login.setUserPassword(testPassword);
        JsonNode loginNode = objectMapper.readTree(postJson("/auth/login", login));
        assertEquals(0, loginNode.get("code").asInt());
        String token = loginNode.get("data").get("token").asText();
        assertFalse(token.isEmpty());
        assertEquals(testAccount, loginNode.get("data").get("user").get("userAccount").asText());
        createdTokens.add(token);

        // ---- Redis 白名单存在：xzh:login:{token} -> userId ----
        User dbUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, testAccount));
        assertNotNull(dbUser);
        assertEquals(String.valueOf(dbUser.getId()),
                stringRedisTemplate.opsForValue().get(jwtProperties.getRedisPrefix() + token));

        // ---- 带 token 访问 /me ----
        JsonNode meNode = objectMapper.readTree(mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(testAccount, meNode.get("data").get("userAccount").asText());

        // ---- 不带 token 访问 /me：未登录 ----
        JsonNode meNoAuth = objectMapper.readTree(mockMvc.perform(get("/auth/me"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(40100, meNoAuth.get("code").asInt());

        // ---- 登出 ----
        JsonNode logoutNode = objectMapper.readTree(mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, logoutNode.get("code").asInt());

        // ---- 登出后旧 token 访问 /me：白名单已删，强制下线 ----
        JsonNode meAfterLogout = objectMapper.readTree(mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(40100, meAfterLogout.get("code").asInt());
        assertEquals("登录已失效，请重新登录", meAfterLogout.get("message").asText());
    }

    @Test
    void register_invalidParams_returnsParamsError() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setUserAccount("abc");        // 账号过短
        register.setUserPassword("123");       // 密码过短
        register.setCheckPassword("123");
        JsonNode node = objectMapper.readTree(postJson("/auth/register", register));
        assertEquals(40000, node.get("code").asInt());
    }

    @Test
    void login_wrongPassword_returnsError() throws Exception {
        // 先注册
        RegisterRequest register = new RegisterRequest();
        register.setUserAccount(testAccount);
        register.setUserPassword(testPassword);
        register.setCheckPassword(testPassword);
        postJson("/auth/register", register);

        // 错误密码登录
        LoginRequest login = new LoginRequest();
        login.setUserAccount(testAccount);
        login.setUserPassword("wrongpass1");
        JsonNode node = objectMapper.readTree(postJson("/auth/login", login));
        assertEquals(40002, node.get("code").asInt());
    }

    private String registerAndGetToken() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setUserAccount(testAccount);
        register.setUserPassword(testPassword);
        register.setCheckPassword(testPassword);
        JsonNode regNode = objectMapper.readTree(postJson("/auth/register", register));
        assertEquals(0, regNode.get("code").asInt(), "注册应成功");

        LoginRequest login = new LoginRequest();
        login.setUserAccount(testAccount);
        login.setUserPassword(testPassword);
        JsonNode loginNode = objectMapper.readTree(postJson("/auth/login", login));
        String token = loginNode.get("data").get("token").asText();
        createdTokens.add(token);
        return token;
    }

    @Test
    void updateProfile_persistsAndMeReflects() throws Exception {
        String token = registerAndGetToken();

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUserName("新昵称");
        req.setUserAvatar("https://test.cos.myqcloud.com/avatar/abc.png");
        req.setUserProfile("热爱学习");

        JsonNode updNode = objectMapper.readTree(mockMvc.perform(put("/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, updNode.get("code").asInt(), "更新资料应成功");

        // /me 应反映最新资料
        JsonNode meNode = objectMapper.readTree(mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("新昵称", meNode.get("data").get("userName").asText());
        assertEquals("热爱学习", meNode.get("data").get("userProfile").asText());
        assertEquals("https://test.cos.myqcloud.com/avatar/abc.png", meNode.get("data").get("userAvatar").asText());
    }

    @Test
    void uploadAvatar_returnsCosUrl() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1, 2, 3});

        String resp = mockMvc.perform(multipart("/auth/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(resp);
        assertEquals(0, node.get("code").asInt(), "头像上传应成功");
        assertTrue(node.get("data").asText().startsWith("https://test.cos.myqcloud.com/avatar/"),
                "头像应返回 COS 直链: " + node.get("data").asText());
    }
}
