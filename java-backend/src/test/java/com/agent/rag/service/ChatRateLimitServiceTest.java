package com.agent.rag.service;

import com.agent.rag.common.RoleConstant;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatRateLimitService 集成测试（真实 Redis）
 * <p>
 * 隔离策略：
 * <ul>
 *   <li>Redis 走 test profile 指定的 db 15；</li>
 *   <li>userId 用测试专用大数（如 9000000000 + ts），避免与真实/其他测试数据冲突；</li>
 *   <li>max-count-per-window 用小阈值 3 覆盖，快速验证拦截逻辑，避免默认 50 跑 50 次。</li>
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
        "cos.client.bucket=test-bucket",
        "security.chat.max-count-per-window=3",
        "security.chat.window-minutes=30"
})
class ChatRateLimitServiceTest {

    @Resource
    private ChatRateLimitService chatRateLimitService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        userId = 9_000_000_000L + ts;
        otherUserId = 8_000_000_000L + ts;
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete("xzh:chat:rate:" + userId);
        stringRedisTemplate.delete("xzh:chat:rate:" + otherUserId);
    }

    @Test
    void consume_userBlocksAfterThreshold_inFixedWindow() {
        // 阈值 3：前 3 次放行，第 4 次拦截
        assertTrue(chatRateLimitService.consume(userId, RoleConstant.USER), "第 1 次应放行");
        assertTrue(chatRateLimitService.consume(userId, RoleConstant.USER), "第 2 次应放行");
        assertTrue(chatRateLimitService.consume(userId, RoleConstant.USER), "第 3 次应放行");
        assertFalse(chatRateLimitService.consume(userId, RoleConstant.USER), "超过阈值应拦截");
    }

    @Test
    void consume_adminNeverBlocked() {
        // admin 不限流：消耗远超阈值仍放行
        for (int i = 0; i < 10; i++) {
            assertTrue(chatRateLimitService.consume(userId, RoleConstant.ADMIN), "admin 第 " + (i + 1) + " 次应放行");
        }
    }

    @Test
    void consume_independentBetweenUsers() {
        // userId 消耗满阈值后，其他用户不受影响
        chatRateLimitService.consume(userId, RoleConstant.USER);
        chatRateLimitService.consume(userId, RoleConstant.USER);
        chatRateLimitService.consume(userId, RoleConstant.USER);
        assertFalse(chatRateLimitService.consume(userId, RoleConstant.USER), "userId 超限应拦截");
        assertTrue(chatRateLimitService.consume(otherUserId, RoleConstant.USER), "其他用户不受影响");
    }
}
