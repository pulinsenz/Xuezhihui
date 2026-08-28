package com.agent.rag.service;

import com.agent.rag.dto.resp.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaptchaService 单元测试（Mockito mock Redis，不连外部依赖）
 * <p>
 * 覆盖：
 * <ul>
 *   <li>create：答案落库 Redis（带 TTL），返回可解码的 PNG 图片 + 挑战 id；</li>
 *   <li>verify：答案正确→true；错误/空白/已消费（重放）/Redis 不可达→false。</li>
 * </ul>
 * 图片渲染在本地 JVM（有字体）即可通过；容器内可读性由 Dockerfile 安装 fonts-dejavu-core 保证。
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        service = new CaptchaService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "expireSeconds", 300);
        ReflectionTestUtils.setField(service, "redisPrefix", "xzh:captcha:");
    }

    @Test
    void create_storesAnswerWithTtlAndReturnsPng() throws Exception {
        // 仅 create 走 opsForValue().set(...)，故在此局部 stub，避免严格模式下其余用例报多余 stub
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        CaptchaVO vo = service.create();

        // 挑战 id 与图片均非空
        assertNotNull(vo.getChallengeId());
        assertTrue(vo.getChallengeId().length() >= 16, "challengeId 应为足够随机的不可预测值");
        assertNotNull(vo.getImageBase64());

        // 答案已落库：xzh:captcha:{id} -> 数字字符串，TTL=300s
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valCaptor.capture(), eq(300L), eq(TimeUnit.SECONDS));
        assertEquals("xzh:captcha:" + vo.getChallengeId(), keyCaptor.getValue());
        assertTrue(valCaptor.getValue().matches("\\d{1,4}"), "答案应为数值: " + valCaptor.getValue());

        // 图片是可解码的合法 PNG
        byte[] png = Base64.getDecoder().decode(vo.getImageBase64());
        assertEquals((byte) 0x89, png[0], "PNG 魔数 1");
        assertEquals((byte) 0x50, png[1], "PNG 魔数 2");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img, "应能解码出图片");
        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
    }

    @Test
    void verify_correctAnswer_returnsTrueAndConsumes() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("51");

        assertTrue(service.verify("abc123", "51"));

        // 消费脚本作用于 xzh:captcha:abc123（GET+DEL 原子消费）
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), keysCaptor.capture());
        assertEquals(List.of("xzh:captcha:abc123"), keysCaptor.getValue());
    }

    @Test
    void verify_wrongAnswer_returnsFalse() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("51");
        assertFalse(service.verify("abc123", "52"));
        assertTrue(service.verify("abc123", " 51 "), "首尾空格应被 trim 后命中，避免用户误输入空格被拒");
    }

    @Test
    void verify_blankInput_returnsFalseWithoutRedis() {
        assertFalse(service.verify(null, null));
        assertFalse(service.verify("", "  "));
        assertFalse(service.verify("abc123", ""));
        verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), anyList());
    }

    @Test
    void verify_replay_returnsFalse() {
        // 同一 challengeId 第二次提交：Lua 返回 nil（key 已被 GET+DEL 消费）→ 拒绝（防重放）
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(null);
        assertFalse(service.verify("abc123", "51"));
    }

    @Test
    void verify_redisDown_returnsFalseFailClosed() {
        // Redis 不可达：宁可误杀不可放行（注册非关键路径），防网络抖动被利用为绕过
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenThrow(new DataAccessException("redis down") {
                });
        assertFalse(service.verify("abc123", "51"));
    }
}
