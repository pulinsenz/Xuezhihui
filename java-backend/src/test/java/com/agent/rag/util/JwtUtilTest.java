package com.agent.rag.util;

import com.agent.rag.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 纯单元测试（不启动 Spring 容器）
 *
 * @author pulinsenz
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("unit-test-secret-key-0123456789abcdef0123456789abcdef");
        props.setExpireHours(1);
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtProperties", props);
    }

    @Test
    void createToken_thenVerifyAndParse_shouldSucceed() {
        String token = jwtUtil.createToken(1001L, "admin");
        assertNotNull(token);
        assertTrue(jwtUtil.verify(token));
        assertEquals(1001L, jwtUtil.getUserId(token));
    }

    @Test
    void verify_tamperedToken_shouldFail() {
        String token = jwtUtil.createToken(1001L, "user");
        String tampered = token.substring(0, token.length() - 3) + "xyz";
        assertFalse(jwtUtil.verify(tampered));
    }

    @Test
    void verify_garbageToken_shouldFail() {
        assertFalse(jwtUtil.verify("not-a-jwt"));
        assertFalse(jwtUtil.verify(""));
    }

    @Test
    void resolveToken_stripsBearerPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        assertEquals("abc.def.ghi", jwtUtil.resolveToken(request));

        MockHttpServletRequest rawRequest = new MockHttpServletRequest();
        rawRequest.addHeader("Authorization", "rawtoken");
        assertEquals("rawtoken", jwtUtil.resolveToken(rawRequest));
    }

    @Test
    void resolveToken_noHeader_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(null, jwtUtil.resolveToken(request));
    }
}
