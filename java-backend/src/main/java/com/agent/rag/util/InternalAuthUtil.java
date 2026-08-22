package com.agent.rag.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口鉴权工具（Java ↔ Python 双向信任）
 * <p>
 * 与 Python 侧 hmac.compare_digest 对称：用 MessageDigest.isEqual 做常量时间比较，
 * 避免普通 equals 的时序侧信道（理论上可逐字符猜测 token）。
 *
 * @author pulinsenz
 */
public final class InternalAuthUtil {

    private InternalAuthUtil() {
    }

    /**
     * 校验内部调用 token：期望值与实际值均非空且常量时间相等
     */
    public static boolean check(String expected, String actual) {
        if (expected == null || expected.isEmpty() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
