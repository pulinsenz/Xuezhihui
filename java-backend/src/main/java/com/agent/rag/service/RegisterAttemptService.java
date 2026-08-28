package com.agent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.config.RegisterSecurityProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 注册防刷计数组件（Redis 原子计数）
 * <p>
 * 与登录的 {@link LoginAttemptService} 同一 Lua 脚本模式：IP 固定窗口内注册次数上限，
 * 防验证码 OCR / 人海解决后的批量注册（第二道网，主防线是 {@link CaptchaService} 算术验证码）。
 * Redis 故障时 fail-open：此时验证码层 fail-closed 兜底（Redis 挂掉验证码无法创建/校验，注册天然被拦），
 * 不会出现"无防护成功注册"的窗口。
 * <p>
 * Redis 键：xzh:register:ip:{ip}
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class RegisterAttemptService {

    /** IP 固定窗口配额：INCR 计数，首次 EXPIRE 定窗口，超出阈值返回 0 */
    private static final DefaultRedisScript<Long> CONSUME_IP_QUOTA_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end\n" +
                    "if c > tonumber(ARGV[2]) then return 0 else return 1 end", Long.class);

    private static final String UNKNOWN = "unknown";

    @Resource
    private RegisterSecurityProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 消耗一次 IP 注册配额
     *
     * @param ip 客户端 IP
     * @return true=允许继续；false=本窗口配额已耗尽应拦截。Redis 故障时 fail-open 返回 true
     */
    public boolean consumeIpQuota(String ip) {
        try {
            Long result = stringRedisTemplate.execute(CONSUME_IP_QUOTA_SCRIPT, List.of(ipKey(ip)),
                    String.valueOf(properties.getIpWindowSeconds()),
                    String.valueOf(properties.getIpMaxPerHour()));
            return result == null || result == 1L;
        } catch (DataAccessException e) {
            log.error("consumeIpQuota 访问 Redis 失败，fail-open 放行. ip={}", ip, e);
            return true;
        }
    }

    private String ipKey(String ip) {
        return "xzh:register:ip:" + StrUtil.blankToDefault(ip, UNKNOWN);
    }
}
