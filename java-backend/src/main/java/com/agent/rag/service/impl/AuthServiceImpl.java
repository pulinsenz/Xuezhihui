package com.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.config.LoginSecurityProperties;
import com.agent.rag.dto.req.LoginRequest;
import com.agent.rag.dto.req.RegisterRequest;
import com.agent.rag.dto.req.UpdateProfileRequest;
import com.agent.rag.dto.resp.LoginResponse;
import com.agent.rag.dto.resp.UserVO;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.AuthService;
import com.agent.rag.service.LoginAttemptService;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.JwtUtil;
import com.agent.rag.util.UploadFileTypeValidator;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private LoginAttemptService loginAttemptService;

    @Resource
    private LoginSecurityProperties loginSecurityProperties;

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
    public LoginResponse login(LoginRequest request, String clientIp) {
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        if (StrUtil.isBlank(userAccount) || StrUtil.isBlank(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号、密码不能为空");
        }
        // 防爆破 1：IP 限流（每次尝试都消耗配额，成功也计入，作粗粒度 flood control）
        if (!loginAttemptService.consumeIpQuota(clientIp)) {
            log.warn("登录限流: ip={}", clientIp);
            throw new BusinessException(ErrorCode.LOGIN_TOO_FREQUENT);
        }
        // 防爆破 2：账号失败锁定
        long lockedSeconds = loginAttemptService.checkLocked(userAccount);
        if (lockedSeconds > 0) {
            log.warn("登录被锁定: userAccount={}, remaining={}s", userAccount, lockedSeconds);
            throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                    "登录失败次数过多，请" + lockRemainMinutes(lockedSeconds) + "分钟后再试");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, userAccount));
        // 账号不存在与密码错误统一提示，避免暴露账号是否存在；
        // 对不存在的账号同样记录失败，避免锁的存在性泄露账号存在与否
        if (user == null || !BCrypt.checkpw(userPassword, user.getUserPassword())) {
            log.warn("登录失败: userAccount={}", userAccount);
            boolean lockedNow = loginAttemptService.recordFailure(userAccount);
            if (lockedNow) {
                // 本次失败刚好达到阈值：立即锁定并提示
                throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                        "登录失败次数过多，请" + lockRemainMinutes(loginSecurityProperties.getLockMinutes() * 60L) + "分钟后再试");
            }
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
        }
        // 登录成功：清除失败计数与锁定
        loginAttemptService.recordSuccess(userAccount);
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

    /** 剩余秒数向上取整为分钟，最小 1 */
    private long lockRemainMinutes(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
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

    @Override
    public void updateProfile(UpdateProfileRequest request) {
        User loginUser = getLoginUser();
        String userName = StrUtil.trimToNull(request.getUserName());
        if (userName == null || userName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称长度应在 1-30 字");
        }
        String profile = StrUtil.trimToNull(request.getUserProfile());
        if (profile != null && profile.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "简介不能超过 200 字");
        }
        // 仅更新允许的字段，角色/密码等敏感字段不可经此修改（防越权）
        User update = new User();
        update.setId(loginUser.getId());
        update.setUserName(userName);
        update.setUserAvatar(StrUtil.trimToNull(request.getUserAvatar()));
        update.setUserProfile(profile);
        update.setEditTime(LocalDateTime.now());
        userMapper.updateById(update);
        log.info("更新资料成功: userId={}", loginUser.getId());
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        // 图片白名单校验：排除 svg 等可执行脚本格式，防存储型 XSS
        UploadFileTypeValidator.checkImage(file);
        User loginUser = getLoginUser();
        String url = fileStorageService.storeAvatar(file, loginUser.getId());
        log.info("头像上传成功: userId={}, url={}", loginUser.getId(), url);
        return url;
    }
}
