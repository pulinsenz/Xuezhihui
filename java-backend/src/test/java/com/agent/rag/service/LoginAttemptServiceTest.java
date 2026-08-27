package com.agent.rag.service;

import com.agent.rag.config.LoginSecurityProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoginAttemptService 集成测试（真实 Redis）
 * <p>
 * 隔离策略：
 * <ul>
 *   <li>Redis 走 test/resources/application.yml 指定的 db 15，不污染本地开发 db 0；</li>
 *   <li>账号用 test_lock_&lt;ts&gt;、IP 用合成 10.x.x.x，避免污染其它测试共用的 127.0.0.1 计数。</li>
 * </ul>
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
class LoginAttemptServiceTest {

    @Resource
    private LoginAttemptService loginAttemptService;

    @Resource
    private LoginSecurityProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String account;
    private String ip;

    @BeforeEach
    void setUp() {
        account = "test_lock_" + System.currentTimeMillis();
        ip = "10.99.0." + (System.currentTimeMillis() % 250 + 1);
    }

    @AfterEach
    void tearDown() {
        // 按构造出的确切 key 清理，避免残留污染
        stringRedisTemplate.delete("xzh:login:fail:" + account);
        stringRedisTemplate.delete("xzh:login:lock:" + account);
        stringRedisTemplate.delete("xzh:login:ip:" + ip);
    }

    @Test
    void consecutiveFailures_lockAccountAtThreshold() {
        // 前 4 次失败未锁定
        assertFalse(loginAttemptService.recordFailure(account));
        assertFalse(loginAttemptService.recordFailure(account));
        assertFalse(loginAttemptService.recordFailure(account));
        assertFalse(loginAttemptService.recordFailure(account));
        assertEquals(0, loginAttemptService.checkLocked(account));
        // 第 5 次失败触发锁定
        assertTrue(loginAttemptService.recordFailure(account));
        assertTrue(loginAttemptService.checkLocked(account) > 0);
    }

    @Test
    void checkLocked_returnsRemainingSecondsWithinLockWindow() {
        for (int i = 0; i < properties.getMaxFailCount(); i++) {
            loginAttemptService.recordFailure(account);
        }
        long remaining = loginAttemptService.checkLocked(account);
        assertTrue(remaining > 0 && remaining <= properties.getLockMinutes() * 60L,
                "剩余秒数应在 1~lockMinutes*60 之间: " + remaining);
    }

    @Test
    void recordSuccess_clearsFailureAndLock_andRestartsCount() {
        for (int i = 0; i < properties.getMaxFailCount(); i++) {
            loginAttemptService.recordFailure(account);
        }
        assertTrue(loginAttemptService.checkLocked(account) > 0);

        loginAttemptService.recordSuccess(account);
        assertEquals(0, loginAttemptService.checkLocked(account));
        // 解锁后计数从 0 重新开始：前 4 次失败不应再次触发锁定
        for (int i = 0; i < properties.getMaxFailCount() - 1; i++) {
            assertFalse(loginAttemptService.recordFailure(account), "清除后第 " + (i + 1) + " 次失败不应锁定");
        }
    }

    @Test
    void consumeIpQuota_blocksAfterThreshold_inFixedWindow() {
        // 第 1..max 次放行，第 max+1 次拦截（固定窗口）
        for (int i = 1; i <= properties.getIpMaxPerMinute(); i++) {
            assertTrue(loginAttemptService.consumeIpQuota(ip), "第 " + i + " 次应放行");
        }
        assertFalse(loginAttemptService.consumeIpQuota(ip), "超限后应拦截");
    }

    @Test
    void consumeIpQuota_independentBetweenIps() {
        String otherIp = "10.99.1." + (System.currentTimeMillis() % 250 + 1);
        try {
            for (int i = 0; i < properties.getIpMaxPerMinute(); i++) {
                loginAttemptService.consumeIpQuota(otherIp);
            }
            // otherIp 已超限，本 IP 不受影响
            assertTrue(loginAttemptService.consumeIpQuota(ip));
        } finally {
            stringRedisTemplate.delete("xzh:login:ip:" + otherIp);
        }
    }
}
