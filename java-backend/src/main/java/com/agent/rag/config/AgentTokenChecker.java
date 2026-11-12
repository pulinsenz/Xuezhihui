package com.agent.rag.config;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 内部鉴权 token 启动校验（fail-closed）：
 * ${AGENT_TOKEN:} 无默认值，漏配环境变量时必须在启动期失败，而不是带着
 * 空 token 上线（Python 侧会拒所有调用，对话全挂）或被猜测的弱默认值放行。
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class AgentTokenChecker {

    @Value("${app.agent.token:}")
    private String agentToken;

    @PostConstruct
    public void check() {
        if (StrUtil.isBlank(agentToken)) {
            throw new IllegalStateException(
                    "AGENT_TOKEN 未配置：Java ↔ Python 内部鉴权必需（.env 中生成：openssl rand -hex 32），启动失败");
        }
        if (agentToken.length() < 32) {
            log.warn("AGENT_TOKEN 长度不足 32 字节，内部鉴权强度偏弱，建议换用 openssl rand -hex 32 生成");
        }
        log.info("AGENT_TOKEN 校验通过");
    }
}
