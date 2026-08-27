package com.agent.rag.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cloudflare Turnstile 人机验证服务
 * <p>
 * 注册防批量账号（薅 LLM token 的机器人）。集成方式：
 * <ul>
 *   <li>前端注册表单渲染 Turnstile widget，用户完成挑战后拿到 token；</li>
 *   <li>后端将 token + secret key 提交 Cloudflare siteverify 校验（服务端校验不可绕过，前端 token 可被伪造绕过 widget 但无法伪造 siteverify 结果）。</li>
 * </ul>
 * 降级策略：未配置 TURNSTILE_SECRET_KEY（本地开发 / 测试环境）时校验直接放行；
 * 生产必须配置 {@code TURNSTILE_SECRET_KEY}，配置后缺失或非法 token 一律拒绝。
 * 该降级与项目既有模式一致（如 ALLOW_MOCK_LLM、JWT_SECRET 无默认值），
 * 但务必注意：**未配置时注册接口是开放的**，上线必须填。
 *
 * @author pulinsens
 */
@Slf4j
@Service
public class TurnstileService {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${app.turnstile.secret-key:}")
    private String secretKey;

    /**
     * 校验 Turnstile token
     *
     * @param token    前端提交的 turnstile 响应 token
     * @param remoteIp 客户端 IP（可空，Cloudflare 用于风控）
     * @return true=通过（含未配置 secret key 的降级放行）；false=校验失败
     */
    public boolean verify(String token, String remoteIp) {
        // 降级：未配置 secret key（本地开发/测试）不拦截；生产配置后强制
        if (StrUtil.isBlank(secretKey)) {
            return true;
        }
        if (StrUtil.isBlank(token)) {
            log.warn("Turnstile 校验失败：token 为空（生产已强制人机验证）");
            return false;
        }
        try {
            String body = "secret=" + urlEncode(secretKey)
                    + "&response=" + urlEncode(token)
                    + (StrUtil.isNotBlank(remoteIp) ? "&remoteip=" + urlEncode(remoteIp) : "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SITEVERIFY_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = JSONUtil.parseObj(resp.body());
            boolean success = Boolean.TRUE.equals(json.getBool("success"));
            if (!success) {
                log.warn("Turnstile 校验失败: {}, 错误码={}", resp.body(), json.get("error-codes"));
            }
            return success;
        } catch (Exception e) {
            // 校验服务不可达：安全起见拒绝（宁可误杀不可放行），避免网络抖动被利用为绕过
            log.error("Turnstile 校验异常", e);
            return false;
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
