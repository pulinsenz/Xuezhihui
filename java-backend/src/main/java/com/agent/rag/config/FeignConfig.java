package com.agent.rag.config;

import cn.hutool.core.util.StrUtil;
import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Feign 配置（调用 Python Agent）：超时 + 内部鉴权 token
 * <p>
 * 连接超时 5s，读取超时 120s（向量化、大文档解析较耗时）；
 * 所有请求自动携带 X-Agent-Token（Python 侧校验）
 *
 * @author pulinsenz
 */
@Configuration
public class FeignConfig {

    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(
                Duration.ofSeconds(5),
                Duration.ofSeconds(120),
                false
        );
    }

    /**
     * 内部调用鉴权：自动带 X-Agent-Token，Python Agent 侧校验
     */
    @Bean
    public RequestInterceptor agentAuthInterceptor(@Value("${app.agent.token:}") String token) {
        return template -> {
            if (StrUtil.isNotBlank(token)) {
                template.header("X-Agent-Token", token);
            }
        };
    }
}
