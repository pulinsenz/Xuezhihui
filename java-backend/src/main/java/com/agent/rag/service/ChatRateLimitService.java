package com.agent.rag.service;

import com.agent.rag.common.RoleConstant;
import com.agent.rag.config.ChatRateLimitProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对话限流组件（Redis 原子计数，防脚本刷 LLM token）
 * <p>
 * 按用户维度固定窗口计数：窗口内对话请求达到阈值即拒绝后续请求，窗口到点自动归零。
 * 管理员（admin）不限流。与登录防爆破（LoginAttemptService）共用 Redis Lua 原子模式，
 * 消除 INCR+EXPIRE 竞态。
 * <p>
 * Redis 故障时 fail-open：对话主路径依赖 Redis 会话记忆（python-agent 读 Redis 取历史），
 * Redis 挂掉对话本就不通，限流放行不会产生"无防护成功对话"的窗口。
 * <p>
 * Redis 键：xzh:chat:rate:{userId}
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class ChatRateLimitService {

    /** 固定窗口配额：INCR 计数，首次 EXPIRE 定窗口，超阈值返回 0 */
    private static final DefaultRedisScript<Long> CONSUME_QUOTA_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end\n" +
                    "if c > tonumber(ARGV[2]) then return 0 else return 1 end", Long.class);

    @Resource
    private ChatRateLimitProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 消耗一次对话配额
     *
     * @param userId 当前用户 id
     * @param role   当前用户角色（admin 不限流）
     * @return true=允许对话；false=本窗口配额已耗尽应拦截。Redis 故障时 fail-open 返回 true
     */
    public boolean consume(Long userId, String role) {
        if (RoleConstant.ADMIN.equals(role)) {
            return true;
        }
        if (userId == null) {
            return true;
        }
        try {
            Long result = stringRedisTemplate.execute(CONSUME_QUOTA_SCRIPT,
                    List.of(rateKey(userId)),
                    String.valueOf(properties.getWindowMinutes() * 60L),
                    String.valueOf(properties.getMaxCountPerWindow()));
            return result == null || result == 1L;
        } catch (DataAccessException e) {
            log.error("consume 访问 Redis 失败，fail-open 放行. userId={}", userId, e);
            return true;
        }
    }

    private String rateKey(Long userId) {
        return "xzh:chat:rate:" + userId;
    }
}
