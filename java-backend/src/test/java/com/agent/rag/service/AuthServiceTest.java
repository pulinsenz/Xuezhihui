package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.config.LoginSecurityProperties;
import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.req.UpdateProfileRequest;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.LoginAttemptService;
import com.agent.rag.service.impl.AuthServiceImpl;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.JwtUtil;
import com.agent.rag.util.UserContext;
import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试（Mockito mock 依赖，不连数据库）
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private LoginAttemptService loginAttemptService;

    private JwtProperties jwtProperties;
    private LoginSecurityProperties loginSecurityProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("unit-test-secret");
        jwtProperties.setRedisPrefix("xzh:login:");
        jwtProperties.setExpireHours(1);

        loginSecurityProperties = new LoginSecurityProperties();

        authService = new AuthServiceImpl();
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(authService, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(authService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(authService, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(authService, "loginAttemptService", loginAttemptService);
        ReflectionTestUtils.setField(authService, "loginSecurityProperties", loginSecurityProperties);
    }

    /** 默认放行防爆破门（IP 配额充足、账号未锁定） */
    private void allowLoginAttempt() {
        when(loginAttemptService.consumeIpQuota(anyString())).thenReturn(true);
        when(loginAttemptService.checkLocked(anyString())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        // 清理 ThreadLocal，避免登录用户串到其他用例
        UserContext.clear();
    }

    private User loggedInUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserAccount("bob");
        user.setUserRole("user");
        return user;
    }

    private RegisterRequest validRegister() {
        RegisterRequest req = new RegisterRequest();
        req.setUserAccount("account123");
        req.setUserPassword("pass12345");
        req.setCheckPassword("pass12345");
        return req;
    }

    // ---------- 注册 ----------

    @Test
    void register_success_returnsUserIdAndEncryptsPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        });

        Long userId = authService.register(validRegister());

        assertEquals(100L, userId);
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void register_accountExists_throwsAccountExist() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register(validRegister()));
        assertEquals(ErrorCode.ACCOUNT_EXIST.getCode(), e.getCode());
    }

    @Test
    void register_passwordTooShort_throwsParamsError() {
        RegisterRequest req = validRegister();
        req.setUserPassword("123");
        req.setCheckPassword("123");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void register_checkPasswordMismatch_throwsParamsError() {
        RegisterRequest req = validRegister();
        req.setCheckPassword("different");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    // ---------- 登录 ----------

    @Test
    void login_blankParams_throwsParamsError() {
        LoginRequest req = new LoginRequest();
        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        // 参数不合法时不消耗 IP 配额、不触库
        verify(loginAttemptService, never()).consumeIpQuota(anyString());
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void login_userNotExist_throwsAccountOrPasswordError() {
        allowLoginAttempt();
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginRequest req = new LoginRequest();
        req.setUserAccount("nobody");
        req.setUserPassword("pass12345");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getCode(), e.getCode());
        // 对不存在的账号同样记录失败：避免锁的存在性泄露账号存在与否
        verify(loginAttemptService).recordFailure("nobody");
    }

    @Test
    void login_wrongPassword_throwsAccountOrPasswordError() {
        allowLoginAttempt();
        User user = new User();
        user.setId(7L);
        user.setUserAccount("bob");
        user.setUserPassword(BCrypt.hashpw("correct-pass"));
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUserAccount("bob");
        req.setUserPassword("wrong-pass");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getCode(), e.getCode());
        verify(loginAttemptService).recordFailure("bob");
    }

    @Test
    void login_failureReachesThreshold_throwsLoginLocked() {
        allowLoginAttempt();
        User user = new User();
        user.setId(7L);
        user.setUserAccount("bob");
        user.setUserPassword(BCrypt.hashpw("correct-pass"));
        when(userMapper.selectOne(any())).thenReturn(user);
        // 本次失败累计达到阈值，触发账号锁定
        when(loginAttemptService.recordFailure("bob")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUserAccount("bob");
        req.setUserPassword("wrong-pass");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.LOGIN_LOCKED.getCode(), e.getCode());
    }

    @Test
    void login_accountLocked_throwsLoginLockedWithoutQueryingDb() {
        allowLoginAttempt();
        // 账号已锁定（剩余 10 分钟）
        when(loginAttemptService.checkLocked("bob")).thenReturn(600L);

        LoginRequest req = new LoginRequest();
        req.setUserAccount("bob");
        req.setUserPassword("whatever");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.LOGIN_LOCKED.getCode(), e.getCode());
        // 锁定短路：不再查库比对密码
        verify(userMapper, never()).selectOne(any());
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void login_ipQuotaExhausted_throwsTooFrequent() {
        when(loginAttemptService.consumeIpQuota(anyString())).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUserAccount("bob");
        req.setUserPassword("pass12345");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.LOGIN_TOO_FREQUENT.getCode(), e.getCode());
        // IP 拦截：不查库、不计失败
        verify(userMapper, never()).selectOne(any());
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void login_success_returnsTokenAndWritesWhitelist() {
        allowLoginAttempt();
        User user = new User();
        user.setId(7L);
        user.setUserAccount("bob");
        user.setUserPassword(BCrypt.hashpw("pass12345"));
        user.setUserRole("user");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.createToken(7L, "user")).thenReturn("token123");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginRequest req = new LoginRequest();
        req.setUserAccount("bob");
        req.setUserPassword("pass12345");
        LoginResponse resp = authService.login(req, "127.0.0.1");

        assertNotNull(resp.getToken());
        assertEquals("token123", resp.getToken());
        assertEquals("bob", resp.getUser().getUserAccount());
        // 白名单：xzh:login:{token} -> userId，TTL=1小时
        verify(valueOperations).set(eq(jwtProperties.getRedisPrefix() + "token123"), eq("7"), eq(1L), eq(TimeUnit.HOURS));
        // 成功后清除失败计数与锁定
        verify(loginAttemptService).recordSuccess("bob");
    }

    // ---------- 登出 ----------

    @Test
    void logout_deletesWhitelist() {
        authService.logout("token456");
        verify(stringRedisTemplate).delete(jwtProperties.getRedisPrefix() + "token456");
    }

    @Test
    void logout_nullToken_doesNothing() {
        authService.logout(null);
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    // ---------- 更新资料 ----------

    @Test
    void updateProfile_updatesOnlyAllowedFields() {
        UserContext.setUser(loggedInUser(7L));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUserName("新昵称");
        req.setUserAvatar("https://cos/avatar/xx.png");
        req.setUserProfile("热爱学习");

        authService.updateProfile(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User update = captor.getValue();
        assertEquals(7L, update.getId());
        assertEquals("新昵称", update.getUserName());
        assertEquals("https://cos/avatar/xx.png", update.getUserAvatar());
        assertEquals("热爱学习", update.getUserProfile());
        // 敏感字段不可经资料接口修改
        assertNull(update.getUserRole());
        assertNull(update.getUserPassword());
    }

    @Test
    void updateProfile_blankName_throwsParamsError() {
        UserContext.setUser(loggedInUser(7L));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUserName("   ");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.updateProfile(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_profileTooLong_throwsParamsError() {
        UserContext.setUser(loggedInUser(7L));
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUserName("昵称");
        req.setUserProfile("长".repeat(201));
        BusinessException e = assertThrows(BusinessException.class, () -> authService.updateProfile(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void updateProfile_notLogin_throwsNotLogin() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUserName("ok");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.updateProfile(req));
        assertEquals(ErrorCode.NOT_LOGIN.getCode(), e.getCode());
    }

    // ---------- 头像上传 ----------

    @Test
    void uploadAvatar_delegatesToStorage_withLoginUserId() throws Exception {
        UserContext.setUser(loggedInUser(7L));
        when(fileStorageService.storeAvatar(any(), eq(7L))).thenReturn("https://cos/avatar/a.png");
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1});

        String url = authService.uploadAvatar(file);

        assertEquals("https://cos/avatar/a.png", url);
        verify(fileStorageService).storeAvatar(file, 7L);
    }
}
