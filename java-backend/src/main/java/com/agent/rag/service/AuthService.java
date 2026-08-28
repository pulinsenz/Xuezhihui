package com.agent.rag.service;

import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.req.UpdateProfileRequest;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证服务
 *
 * @author pulinsenz
 */
public interface AuthService {

    /**
     * 注册
     *
     * @param request  注册请求（含算术验证码 challengeId + 答案）
     * @param clientIp 客户端 IP（由 Controller 解析传入，服务层不依赖 servlet），用于注册 IP 限流
     * @return 新用户 id
     */
    Long register(RegisterRequest request, String clientIp);

    /**
     * 登录：签发 JWT 并写入 Redis 白名单
     * <p>防爆破：失败锁定 + IP 限流，详见 {@link LoginAttemptService}
     *
     * @param request  登录请求
     * @param clientIp 客户端 IP（由 Controller 解析传入，服务层不依赖 servlet）
     * @return token + 用户信息
     */
    LoginResponse login(LoginRequest request, String clientIp);

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

    /**
     * 更新当前用户资料（昵称/头像/简介）
     *
     * @param request 资料请求
     */
    void updateProfile(UpdateProfileRequest request);

    /**
     * 上传当前用户头像，返回可公网加载的 URL
     *
     * @param file 头像图片
     * @return 头像 URL
     */
    String uploadAvatar(MultipartFile file);
}
