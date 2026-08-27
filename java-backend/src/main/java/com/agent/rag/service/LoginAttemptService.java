package com.agent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.config.LoginSecurityProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 登录防爆破计数组件（Redis 原子计数）
 * <p>
 * 两层防护：
 * <ul>
 *   <li>账号维度：连续失败达到阈值锁定账号，lock key 存活期内拒绝登录；</li>
 *   <li>IP 维度：固定窗口内登录尝试次数上限，防分布式账号喷洒（尽力而为，X-Forwarded-For 可伪造）。</li>
 * </ul>
 * 计数与锁定均用 Lua 脚本原子完成（单实例下串行执行），消除 INCR+EXPIRE 与并发到阈值的竞态。
 * Redis 故障时 fail-open：登录主路径本来就要把 JWT 写入 Redis 白名单，Redis 挂掉登录不可能成功，
 * 安全门放行只会把报错从限流码变成系统错误，不会产生"无防护成功登录"的窗口。
 * <p>
 * Redis 键：xzh:login:fail:{account} / xzh:login:lock:{account} / xzh:login:ip:{ip}
 * （与 JWT 白名单 xzh:login:{token} 不冲突，token 为 base64url，不含冒号子段）
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class LoginAttemptService {

    /** 记录一次失败：未被锁则 INCR fail（刷新 TTL=滑动窗口），达到阈值则 SET lock 并 DEL fail（解锁后从 0 重新计数） */
    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[2]) == 1 then return 2 end\n" +
                    "local c = redis.call('INCR', KEYS[1])\n" +
                    "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n" +
                    "if c >= tonumber(ARGV[2]) then\n" +
                    "    redis.call('SET', KEYS[2], 1, 'EX', tonumber(ARGV[3]))\n" +
                    "    redis.call('DEL', KEYS[1])\n" +
                    "    return 1\n" +
                    "end\n" +
                    "return 0", Long.class);

    /** 查询锁剩余秒数：缺失 key 的 TTL 为 -2，天然归 0 */
    private static final DefaultRedisScript<Long> CHECK_LOCKED_SCRIPT = new DefaultRedisScript<>(
            "local t = redis.call('TTL', KEYS[1])\n" +
                    "if t and t > 0 then return t end\n" +
                    "return 0", Long.class);

    /** IP 固定窗口配额：INCR 计数，首次 EXPIRE 定窗口 */
    private static final DefaultRedisScript<Long> CONSUME_IP_QUOTA_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end\n" +
                    "if c > tonumber(ARGV[2]) then return 0 else return 1 end", Long.class);

    private static final String UNKNOWN = "unknown";

    @Resource
    private LoginSecurityProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询账号锁剩余秒数
     *
     * @param account 账号
     * @return 剩余锁定秒数；未锁定返回 0。Redis 故障时 fail-open 返回 0
     */
    public long checkLocked(String account) {
        try {
            Long ttl = stringRedisTemplate.execute(CHECK_LOCKED_SCRIPT, List.of(lockKey(account)));
            return ttl == null ? 0 : ttl;
        } catch (DataAccessException e) {
            log.error("checkLocked 访问 Redis 失败，fail-open 放行. account={}", account, e);
            return 0;
        }
    }

    /**
     * 记录一次失败登录，计数达阈值时锁定账号
     *
     * @param account 账号
     * @return true=本次失败触发了账号锁定；false=未触发。Redis 故障时 fail-open 返回 false
     */
    public boolean recordFailure(String account) {
        try {
            Long result = stringRedisTemplate.execute(RECORD_FAILURE_SCRIPT,
                    Arrays.asList(failKey(account), lockKey(account)),
                    String.valueOf(properties.getFailWindowMinutes() * 60L),
                    String.valueOf(properties.getMaxFailCount()),
                    String.valueOf(properties.getLockMinutes() * 60L));
            return result != null && result == 1L;
        } catch (DataAccessException e) {
            log.error("recordFailure 访问 Redis 失败，fail-open 不锁定. account={}", account, e);
            return false;
        }
    }

    /**
     * 登录成功后清除失败计数与锁定
     */
    public void recordSuccess(String account) {
        try {
            stringRedisTemplate.delete(failKey(account));
            stringRedisTemplate.delete(lockKey(account));
        } catch (DataAccessException e) {
            log.error("recordSuccess 访问 Redis 失败，忽略清理. account={}", account, e);
        }
    }

    /**
     * 消耗一次 IP 登录配额
     *
     * @param ip 客户端 IP
     * @return true=允许继续；false=本窗口配额已耗尽应拦截。Redis 故障时 fail-open 返回 true
     */
    public boolean consumeIpQuota(String ip) {
        try {
            Long result = stringRedisTemplate.execute(CONSUME_IP_QUOTA_SCRIPT, List.of(ipKey(ip)),
                    String.valueOf(properties.getIpWindowSeconds()),
                    String.valueOf(properties.getIpMaxPerMinute()));
            return result == null || result == 1L;
        } catch (DataAccessException e) {
            log.error("consumeIpQuota 访问 Redis 失败，fail-open 放行. ip={}", ip, e);
            return true;
        }
    }

    private String failKey(String account) {
        return "xzh:login:fail:" + StrUtil.blankToDefault(account, UNKNOWN);
    }

    private String lockKey(String account) {
        return "xzh:login:lock:" + StrUtil.blankToDefault(account, UNKNOWN);
    }

    private String ipKey(String ip) {
        return "xzh:login:ip:" + StrUtil.blankToDefault(ip, UNKNOWN);
    }
}
