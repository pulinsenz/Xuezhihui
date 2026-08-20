package com.agent.rag.service;

import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.entity.User;

/**
 * 认证服务
 *
 * @author pulinsenz
 */
public interface AuthService {

    /**
     * 注册
     *
     * @param request 注册请求
     * @return 新用户 id
     */
    Long register(RegisterRequest request);

    /**
     * 登录：签发 JWT 并写入 Redis 白名单
     *
     * @param request 登录请求
     * @return token + 用户信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 登出：删除 Redis 白名单，token 立即失效
     *
     * @param token 裸 token
     */
    void logout(String token);

    /**
     * 获取当前登录用户（来自 ThreadLocal，由拦截器填充）
     */
    User getLoginUser();
}
