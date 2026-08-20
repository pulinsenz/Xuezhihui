package com.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.dto.resp.UserVO;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.AuthService;
import com.agent.rag.util.JwtUtil;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long register(RegisterRequest request) {
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        // 参数校验
        if (StrUtil.isBlank(userAccount) || StrUtil.isBlank(userPassword) || StrUtil.isBlank(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号、密码不能为空");
        }
        if (userAccount.length() < 4 || userAccount.length() > 32) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度应在 4-32 位");
        }
        if (userPassword.length() < 8 || userPassword.length() > 32) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度应在 8-32 位");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 账号唯一校验
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, userAccount));
        if (count > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXIST);
        }
        // 密码 BCrypt 加密存储
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(BCrypt.hashpw(userPassword));
        user.setUserName(StrUtil.isBlank(request.getUserName()) ? userAccount : request.getUserName());
        user.setUserRole("user");
        user.setEditTime(LocalDateTime.now());
        userMapper.insert(user);
        log.info("注册成功: userId={}, userAccount={}", user.getId(), userAccount);
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        if (StrUtil.isBlank(userAccount) || StrUtil.isBlank(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号、密码不能为空");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, userAccount));
        // 账号不存在与密码错误统一提示，避免暴露账号是否存在
        if (user == null || !BCrypt.checkpw(userPassword, user.getUserPassword())) {
            log.warn("登录失败: userAccount={}", userAccount);
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
        }
        // 签发 JWT
        String token = jwtUtil.createToken(user.getId(), user.getUserRole());
        // 写入 Redis 白名单，TTL 与 token 过期时间一致，支持强制下线
        stringRedisTemplate.opsForValue().set(
                jwtProperties.getRedisPrefix() + token,
                String.valueOf(user.getId()),
                jwtProperties.getExpireHours(),
                TimeUnit.HOURS);
        log.info("登录成功: userId={}, userAccount={}", user.getId(), userAccount);
        return new LoginResponse(token, UserVO.from(user));
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token);
            log.info("登出成功, 白名单已删除");
        }
    }

    @Override
    public User getLoginUser() {
        User user = UserContext.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return user;
    }
}
