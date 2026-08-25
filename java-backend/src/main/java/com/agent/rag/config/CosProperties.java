package com.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 COS 对象存储配置（绑定 application.yml 的 cos.client.*）
 * <p>
 * 知识库封面等需要公网可访问的图片走 COS；文档文件仍走本地共享卷（LocalFileStorageService）。
 *
 * @author pulinsenz
 */
@Data
@Component
@ConfigurationProperties(prefix = "cos.client")
public class CosProperties {

    /**
     * 访问域名，如 https://picture-1391878614.cos.ap-guangzhou.myqcloud.com
     */
    private String host;

    /**
     * SecretId
     */
    private String secretId;

    /**
     * SecretKey
     */
    private String secretKey;

    /**
     * 地域，如 ap-guangzhou
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucket;
}
