package com.agent.rag.util;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具
 * <p>
 * 依次尝试代理头再回退 remoteAddr：X-Forwarded-For 取逗号分隔的首个（最接近真实客户端）IP，
 * 避免伪造链注入（如 X-Forwarded-For: 1.2.3.4, evil）。注意：代理头可被直连客户端伪造，
 * 若经反向代理访问，应由代理清空/覆写这些头后仅追加可信的 XFF。
 *
 * @author pulinsenz
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    /**
     * 获取客户端 IP，无法解析时返回 "unknown"
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = firstForwardedIp(request);
        if (isInvalid(ip)) {
            ip = trimHeader(request.getHeader("X-Real-IP"));
        }
        if (isInvalid(ip)) {
            ip = trimHeader(request.getHeader("Proxy-Client-IP"));
        }
        if (isInvalid(ip)) {
            ip = trimHeader(request.getHeader("WL-Proxy-Client-IP"));
        }
        if (isInvalid(ip)) {
            ip = request.getRemoteAddr();
        }
        return isInvalid(ip) ? UNKNOWN : ip;
    }

    /** X-Forwarded-For 形如 "client, proxy1, proxy2"，取首个客户端 IP */
    private static String firstForwardedIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        return comma > 0 ? value.substring(0, comma).trim() : value.trim();
    }

    private static String trimHeader(String value) {
        return StrUtil.trimToNull(value);
    }

    private static boolean isInvalid(String ip) {
        return StrUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }
}
