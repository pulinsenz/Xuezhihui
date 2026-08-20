package com.agent.rag.interceptor;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.util.JwtUtil;
import com.agent.rag.util.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 JWT 签名 + Redis 白名单，将用户放入 ThreadLocal
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = jwtUtil.resolveToken(request);
        if (StrUtil.isBlank(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        // 1. 校验签名与过期时间
        if (!jwtUtil.verify(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "token 无效或已过期");
        }
        // 2. 校验 Redis 白名单（支持强制下线）
        String redisKey = jwtProperties.getRedisPrefix() + token;
        String userIdStr = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isBlank(userIdStr)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
        }
        // 3. 加载用户
        User user = userMapper.selectById(userIdStr);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户不存在");
        }
        UserContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程复用污染
        UserContext.clear();
    }
}
