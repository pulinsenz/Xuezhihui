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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "cos.client.host=https://test.cos.myqcloud.com",
        "cos.client.secret-id=test-secret-id",
        "cos.client.secret-key=test-secret-key",
        "cos.client.region=ap-guangzhou",
        "cos.client.bucket=test-bucket"
        // 注册/登录 IP 限流豁免见 src/test/resources/application-test.yml（集成测试共享 127.0.0.1）
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
        // 清理本测试账号的防爆破计数键（fail/lock），避免残留
        stringRedisTemplate.delete("xzh:login:fail:" + testAccount);
        stringRedisTemplate.delete("xzh:login:lock:" + testAccount);
    }

    private String postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 获取一道新算术验证码并从 Redis 读答案（测试环境可直接访问 Redis），构造完整注册请求 */
    private RegisterRequest validRegisterWithCaptcha() throws Exception {
        JsonNode captchaNode = objectMapper.readTree(mockMvc.perform(get("/auth/captcha"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, captchaNode.get("code").asInt(), "验证码获取应成功");
        String challengeId = captchaNode.get("data").get("challengeId").asText();
        String answer = stringRedisTemplate.opsForValue().get("xzh:captcha:" + challengeId);
        assertNotNull(answer, "验证码答案应已写入 Redis: " + challengeId);

        RegisterRequest req = new RegisterRequest();
        req.setUserAccount(testAccount);
        req.setUserPassword(testPassword);
        req.setCheckPassword(testPassword);
        req.setCaptchaId(challengeId);
        req.setCaptchaAnswer(answer);
        return req;
    }

    @Test
    void register_login_me_logout_fullFlow() throws Exception {
        // ---- 注册 ----
        RegisterRequest register = validRegisterWithCaptcha();
        JsonNode regNode = objectMapper.readTree(postJson("/auth/register", register));
        assertEquals(0, regNode.get("code").asInt(), "注册应成功");
        assertTrue(regNode.get("data").asLong() > 0, "应返回 userId");

        // ---- 重复注册：账号已存在（需重新获取验证码，因为上一个已被一次性消费）----
        JsonNode dupNode = objectMapper.readTree(postJson("/auth/register", validRegisterWithCaptcha()));
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
    void getCaptcha_returnsChallengeAndImage() throws Exception {
        JsonNode node = objectMapper.readTree(mockMvc.perform(get("/auth/captcha"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, node.get("code").asInt(), "验证码获取应成功");
        assertFalse(node.get("data").get("challengeId").asText().isEmpty());
        assertFalse(node.get("data").get("imageBase64").asText().isEmpty());
    }

    @Test
    void register_wrongCaptcha_rejects() throws Exception {
        // 拿一道验证码但提交错误答案 → 必须拒绝且不写入用户
        JsonNode captchaNode = objectMapper.readTree(mockMvc.perform(get("/auth/captcha"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        RegisterRequest req = new RegisterRequest();
        req.setUserAccount(testAccount);
        req.setUserPassword(testPassword);
        req.setCheckPassword(testPassword);
        req.setCaptchaId(captchaNode.get("data").get("challengeId").asText());
        req.setCaptchaAnswer("99999"); // 故意写错

        JsonNode node = objectMapper.readTree(postJson("/auth/register", req));
        assertEquals(40000, node.get("code").asInt(), "验证码错误应拒绝注册");
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, testAccount));
        assertEquals(0, count, "验证码错误不得写入用户");
    }

    @Test
    void register_captchaReplay_rejects() throws Exception {
        // 同一验证码提交两次：第一次成功消费，第二次必须被拒（防重放）。
        // 同账号第二次若验证码未被消费会返回"账号已存在"(40001)，断言 40000 可证明验证码已被一次性消费
        JsonNode captchaNode = objectMapper.readTree(mockMvc.perform(get("/auth/captcha"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String challengeId = captchaNode.get("data").get("challengeId").asText();
        String answer = stringRedisTemplate.opsForValue().get("xzh:captcha:" + challengeId);

        RegisterRequest req = new RegisterRequest();
        req.setUserAccount(testAccount);
        req.setUserPassword(testPassword);
        req.setCheckPassword(testPassword);
        req.setCaptchaId(challengeId);
        req.setCaptchaAnswer(answer);

        JsonNode first = objectMapper.readTree(postJson("/auth/register", req));
        assertEquals(0, first.get("code").asInt(), "第一次提交应成功");
        JsonNode replay = objectMapper.readTree(postJson("/auth/register", req));
        assertEquals(40000, replay.get("code").asInt(), "同一验证码二次提交应被拒（防重放）");
        assertTrue(replay.get("message").asText().contains("验证码"), "错误信息应提示验证码: " + replay.get("message").asText());
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
    void register_accountWithLeadingTrailingBlanks_trimmedThenLoginSucceeds() throws Exception {
        // 回归（bug：账号开头带空格原样入库，用户按所见账号登录失败）：
        // 带首尾空格注册 → 库中应为 trim 后的账号，且用 trim 后账号能正常登录
        RegisterRequest register = validRegisterWithCaptcha();
        register.setUserAccount("  " + testAccount + "  ");
        JsonNode regNode = objectMapper.readTree(postJson("/auth/register", register));
        assertEquals(0, regNode.get("code").asInt(), "带首尾空格的账号应 trim 后注册成功");

        // 库里不应存在带空格的原始账号
        Long raw = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "  " + testAccount + "  "));
        assertEquals(0, raw, "库中不应存在首尾带空格的账号");
        Long trimmed = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, testAccount));
        assertEquals(1, trimmed, "库中应存在 trim 后的账号");

        // 用户按所见账号（trim 后）登录应成功
        LoginRequest login = new LoginRequest();
        login.setUserAccount(testAccount);
        login.setUserPassword(testPassword);
        JsonNode loginNode = objectMapper.readTree(postJson("/auth/login", login));
        assertEquals(0, loginNode.get("code").asInt(), "trim 后的账号应能登录");
        createdTokens.add(loginNode.get("data").get("token").asText());
        assertEquals(testAccount, loginNode.get("data").get("user").get("userAccount").asText());
    }

    @Test
    void login_wrongPassword_returnsError() throws Exception {
        // 先注册
        postJson("/auth/register", validRegisterWithCaptcha());

        // 错误密码登录
        LoginRequest login = new LoginRequest();
        login.setUserAccount(testAccount);
        login.setUserPassword("wrongpass1");
        JsonNode node = objectMapper.readTree(postJson("/auth/login", login));
        assertEquals(40002, node.get("code").asInt());
    }

    @Test
    void login_bruteForce_locksAccount() throws Exception {
        // 先注册
        postJson("/auth/register", validRegisterWithCaptcha());

        // 连续错误密码：前 4 次返回"账号或密码错误"，第 5 次触发锁定
        LoginRequest wrong = new LoginRequest();
        wrong.setUserAccount(testAccount);
        wrong.setUserPassword("wrongpass1");
        for (int i = 1; i <= 4; i++) {
            JsonNode node = objectMapper.readTree(postJson("/auth/login", wrong));
            assertEquals(40002, node.get("code").asInt(), "第 " + i + " 次失败应返回账号或密码错误");
        }
        JsonNode lockNode = objectMapper.readTree(postJson("/auth/login", wrong));
        assertEquals(40003, lockNode.get("code").asInt(), "第 5 次失败应触发账号锁定");
        assertTrue(lockNode.get("message").asText().contains("分钟后再试"),
                "锁定提示应包含剩余时间: " + lockNode.get("message").asText());

        // 锁定期间即使密码正确也返回 40003
        LoginRequest correct = new LoginRequest();
        correct.setUserAccount(testAccount);
        correct.setUserPassword(testPassword);
        JsonNode stillLocked = objectMapper.readTree(postJson("/auth/login", correct));
        assertEquals(40003, stillLocked.get("code").asInt(), "锁定期间正确密码也不应放行");
    }

    private String registerAndGetToken() throws Exception {
        JsonNode regNode = objectMapper.readTree(postJson("/auth/register", validRegisterWithCaptcha()));
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

    @Test
    void uploadAvatar_rejectsNonImageType() throws Exception {
        String token = registerAndGetToken();
        // 非图片扩展名（含 svg 可嵌脚本格式）必须被拒
        String[] banned = {"evil.txt", "logo.svg", "shell.sh"};
        for (String filename : banned) {
            MockMultipartFile file = new MockMultipartFile("file", filename,
                    "application/octet-stream", new byte[]{1, 2, 3});
            String resp = mockMvc.perform(multipart("/auth/avatar")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(resp);
            assertEquals(40000, node.get("code").asInt(), filename + " 应被头像白名单拒绝");
        }
    }

    @Test
    void changePassword_invalidatesOldToken_andAllowsLoginWithNewPassword() throws Exception {
        String token = registerAndGetToken();

        Map<String, String> body = Map.of(
                "oldPassword", testPassword,
                "newPassword", "newpass456",
                "checkPassword", "newpass456"
        );
        JsonNode changeNode = objectMapper.readTree(mockMvc.perform(put("/auth/password")
                        .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, changeNode.get("code").asInt(), "修改密码应成功");
        String newToken = changeNode.get("data").get("token").asText();
        createdTokens.add(newToken);

        JsonNode oldTokenMe = objectMapper.readTree(mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(40100, oldTokenMe.get("code").asInt(), "旧 token 应失效");

        JsonNode newTokenMe = objectMapper.readTree(mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + newToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(testAccount, newTokenMe.get("data").get("userAccount").asText(), "新 token 应立即可用");
    }
}
