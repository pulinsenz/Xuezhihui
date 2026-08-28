package com.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 注册防刷配置（IP 限流，配合自研算术验证码）
 * <p>
 * 验证码是防批量注册的主防线；IP 限流作第二道网，防验证码 OCR / 人海解决后的批量注册。
 * 生产可用环境变量 SECURITY_REGISTER_IP_MAX_PER_HOUR / SECURITY_REGISTER_IP_WINDOW_SECONDS 覆盖
 * （Spring 自动绑定，无需改代码）。NAT 共享出口 IP 的办公/校园网可调大阈值。
 *
 * @author pulinsenz
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.register")
public class RegisterSecurityProperties {

    /**
     * 单 IP 每小时注册次数上限（成功也计入，作粗粒度 flood control）
     */
    private int ipMaxPerHour = 10;

    /**
     * IP 计数固定窗口（秒）
     */
    private int ipWindowSeconds = 3600;
}
