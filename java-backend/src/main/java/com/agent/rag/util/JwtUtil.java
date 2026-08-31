package com.agent.rag.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.agent.rag.config.JwtProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类（hutool-jwt，HS256）
 *
 * @author pulinsenz
 */
@Component
public class JwtUtil {

    @Resource
    private JwtProperties jwtProperties;

    /**
     * 生成 token，payload 携带 userId、userRole
     */
    public String createToken(Long userId, String userRole) {
        Date expire = DateUtil.offsetHour(new Date(), (int) jwtProperties.getExpireHours());
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("userRole", userRole)
                .setPayload("jti", UUID.randomUUID().toString())
                .setExpiresAt(expire)
                .setKey(jwtProperties.getSecret().getBytes())
                .sign();
    }

    /**
     * 校验 token（签名 + 过期时间）
     */
    public boolean verify(String token) {
        try {
            return JWTUtil.parseToken(token)
                    .setKey(jwtProperties.getSecret().getBytes())
                    .validate(0);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 token 中的 userId
     */
    public Long getUserId(String token) {
        JWT jwt = JWTUtil.parseToken(token).setKey(jwtProperties.getSecret().getBytes());
        return Long.valueOf(String.valueOf(jwt.getPayload("userId")));
    }

    /**
     * 从请求头解析出裸 token（去掉 Bearer 前缀）
     */
    public String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(jwtProperties.getHeader());
        if (StrUtil.isBlank(auth)) {
            return null;
        }
        String prefix = jwtProperties.getTokenPrefix();
        if (StrUtil.isNotBlank(prefix) && auth.startsWith(prefix)) {
            return auth.substring(prefix.length());
        }
        return auth;
    }
}
