package com.agent.rag.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TurnstileService 单元测试
 * <p>
 * 覆盖无需真实网络的分支：
 * <ul>
 *   <li>未配置 secret-key（本地/测试）→ 降级放行，避免误伤开发；</li>
 *   <li>已配置 secret-key 但 token 为空 → 必须拒绝（防绕过 widget 直连注册接口）。</li>
 * </ul>
 * 第三个分支（token 非空 + 调用 Cloudflare siteverify）依赖外部网络，
 * 由 AuthServiceTest 以 mock turnstileService 断言"校验失败拒绝注册"覆盖。
 */
class TurnstileServiceTest {

    private final TurnstileService service = new TurnstileService();

    @Test
    void verify_noSecretKeyConfigured_degradesOpen() {
        // secret-key 为空：本地开发/测试环境降级放行
        ReflectionTestUtils.setField(service, "secretKey", "");
        assertTrue(service.verify("whatever-token", "127.0.0.1"));
        assertTrue(service.verify(null, null), "未配置时缺 token 也放行（否则本地注册不可用）");
    }

    @Test
    void verify_secretKeyConfiguredButTokenBlank_rejects() {
        // 生产配置了 secret-key：缺失 token 必须拒绝（防绕过前端 widget 直连）
        ReflectionTestUtils.setField(service, "secretKey", "test-secret-key");
        assertFalse(service.verify("", "127.0.0.1"));
        assertFalse(service.verify(null, "127.0.0.1"));
    }
}
