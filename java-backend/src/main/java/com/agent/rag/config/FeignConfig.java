package com.agent.rag.config;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Feign 超时配置（调用 Python Agent）
 * <p>
 * 连接超时 5s，读取超时 120s（向量化、大文档解析较耗时）
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
}
