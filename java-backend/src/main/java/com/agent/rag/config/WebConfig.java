package com.agent.rag.config;

import com.agent.rag.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置：拦截器 + 跨域 + 静态资源
 *
 * @author pulinsenz
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    @Value("${app.file.storage.local-path:./data/files}")
    private String localPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 登录注册无需鉴权（含注册前置的验证码获取）
                        "/auth/login",
                        "/auth/register",
                        "/auth/captcha",
                        // 内部接口：Python Agent 工具回调，无 JWT，改由 X-Agent-Token 在 Controller 内校验
                        "/internal/**",
                        // 知识库封面静态资源：<img> 无法携带 JWT，须公开；仅 cover/ 子目录被映射，不含用户文档
                        "/files/**",
                        // 接口文档 (OpenAPI 3 / Knife4j)
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/swagger-ui/**",
                        "/favicon.ico",
                        // 错误页
                        "/error"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 公网图片静态资源：/api/files/** → {local-path}/cover/ 与 {local-path}/avatar/
        // 因 context-path=/api，浏览器实际访问 /api/files/xxx（前端走 /api 代理即可）。
        // 只暴露 cover/（知识库封面）、avatar/（头像）子目录，用户上传的文档文件不在此目录，避免未授权下载。
        // toUri() 生成标准 file:///D:/.../ 形式，避免 Windows 反斜杠拼 file: URL 解析失败
        Path base = Paths.get(localPath).toAbsolutePath().normalize();
        String cover = base.resolve("cover").toUri().toString();
        String avatar = base.resolve("avatar").toUri().toString();
        registry.addResourceHandler("/files/**").addResourceLocations(cover, avatar);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}