package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.dto.resp.UserVO;
import com.agent.rag.entity.User;
import com.agent.rag.service.AuthService;
import com.agent.rag.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private JwtUtil jwtUtil;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
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
}
