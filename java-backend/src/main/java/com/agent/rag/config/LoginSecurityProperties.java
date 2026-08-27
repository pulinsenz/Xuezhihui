package com.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录防爆破配置（账号失败锁定 + IP 限流）
 *
 * @author pulinsenz
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.login")
public class LoginSecurityProperties {

    /**
     * 连续失败次数达到该阈值即锁定账号
     */
    private int maxFailCount = 5;

    /**
     * 失败计数窗口（分钟），滑动窗口：每次失败刷新 TTL
     */
    private int failWindowMinutes = 30;

    /**
     * 锁定时长（分钟）
     */
    private int lockMinutes = 15;

    /**
     * 单 IP 每分钟登录尝试上限（成功也计入，作粗粒度 flood control）
     */
    private int ipMaxPerMinute = 20;

    /**
     * IP 计数固定窗口（秒）
     */
    private int ipWindowSeconds = 60;
}
