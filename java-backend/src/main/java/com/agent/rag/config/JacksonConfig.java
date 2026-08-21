package com.agent.rag.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置：Long/long 序列化为字符串
 * <p>
 * 雪花 ID 超出 JavaScript Number 安全整数范围（2^53），
 * 若输出为 JSON 数字，前端解析会丢精度。全局转字符串根治此问题，
 * 同时保证 create/register 等返回的裸 Long id 也为字符串。
 *
 * @author pulinsenz
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
