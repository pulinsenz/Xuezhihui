package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.req.UpdateProfileRequest;
import com.agent.rag.dto.resp.CaptchaVO;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.dto.resp.UserVO;
import com.agent.rag.entity.User;
import com.agent.rag.service.AuthService;
import com.agent.rag.service.CaptchaService;
import com.agent.rag.util.IpUtil;
import com.agent.rag.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证接口：注册、登录、登出、当前用户
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 获取注册算术验证码（公开免鉴权；注册前先调用，提交时回传 challengeId + 答案）
     */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.create());
    }

    /**
     * 注册（防批量机器人：算术验证码一次性校验 + IP 限流）
     */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return Result.success(authService.register(request, IpUtil.getClientIp(httpRequest)));
    }

    /**
     * 登录（含防爆破：账号失败锁定 + IP 限流）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        return Result.success(authService.login(request, clientIp));
    }

    /**
     * 登出（删除 Redis 白名单，token 立即失效）
     */
    @PostMapping("/logout")
    public Result<Boolean> logout(HttpServletRequest request) {
        authService.logout(jwtUtil.resolveToken(request));
        return Result.success(true);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = authService.getLoginUser();
        return Result.success(UserVO.from(user));
    }

    /**
     * 更新个人资料（昵称/头像/简介）
     */
    @PutMapping("/profile")
    public Result<Boolean> updateProfile(@RequestBody UpdateProfileRequest request) {
        authService.updateProfile(request);
        return Result.success(true);
    }

    /**
     * 上传头像，返回可公网加载的 URL
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return Result.success(authService.uploadAvatar(file));
    }
}
