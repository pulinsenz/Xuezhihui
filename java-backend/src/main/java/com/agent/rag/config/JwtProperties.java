package com.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 鉴权配置
 *
 * @author pulinsenz
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥（生产环境务必替换）
     */
    private String secret;

    /**
     * token 过期时长（小时）
     */
    private long expireHours = 24;

    /**
     * 请求头名称
     */
    private String header = "Authorization";

    /**
     * token 前缀
     */
    private String tokenPrefix = "Bearer ";

    /**
     * Redis 白名单 key 前缀
     */
    private String redisPrefix = "xzh:login:";
}
