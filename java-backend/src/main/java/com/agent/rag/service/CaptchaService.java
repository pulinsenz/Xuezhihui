package com.agent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.dto.resp.CaptchaVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 自研算术验证码（注册防批量机器人，替代 Cloudflare Turnstile）
 * <p>
 * 方案：
 * <ul>
 *   <li>后端随机生成一道算术题，Java2D 绘制成 PNG（噪点 + 干扰线 + 逐字符随机旋转/颜色），base64 返回；</li>
 *   <li>答案存 Redis（{@code xzh:captcha:{challengeId}}），TTL 后自动过期；</li>
 *   <li>注册时 {@link #verify} 用 Lua 脚本 GET+DEL 原子消费——验证码只可用一次，
 *       错一次即作废，机器人无法对同一题穷举、也无法重放。</li>
 * </ul>
 * 失败语义：Redis 不可达时 fail-closed 拒绝（注册非关键路径，宁可误杀不可放行），
 * 与验证码主防线的定位一致（防薅 LLM token 的批量注册）。
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class CaptchaService {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 46;
    private static final int FONT_SIZE = 18;
    /** 乘法用个位数，保证答案好算；加减用两位数 */
    private static final int A_MIN = 10, A_MAX = 99;

    /** 一次性消费：GET 后立即 DEL，防重放与穷举 */
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local a = redis.call('GET', KEYS[1])\n" +
                    "if a then redis.call('DEL', KEYS[1]) end\n" +
                    "return a", String.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.captcha.expire-seconds:300}")
    private int expireSeconds;

    @Value("${app.captcha.redis-prefix:xzh:captcha:}")
    private String redisPrefix;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成一道算术验证码：答案落库 Redis，图片以 base64 返回
     *
     * @return 挑战 id + 图片 base64
     */
    public CaptchaVO create() {
        int a = randomInt(A_MIN, A_MAX);
        int b = randomInt(A_MIN, A_MAX);
        int answer;
        String text;
        switch (secureRandom.nextInt(3)) {
            case 0 -> {
                answer = a + b;
                text = a + " + " + b;
            }
            case 1 -> {
                if (a < b) { // 减法结果恒为非负，避免负号
                    int t = a;
                    a = b;
                    b = t;
                }
                answer = a - b;
                text = a + " - " + b;
            }
            default -> {
                // 乘法：个位数相乘，答案 ≤ 81，便于口算
                int x = randomInt(2, 9);
                int y = randomInt(3, 9);
                answer = x * y;
                text = x + " x " + y;
            }
        }

        String challengeId = newChallengeId();
        String answerStr = Integer.toString(answer);
        byte[] png;
        try {
            png = renderPng(text + " = ?");
        } catch (IOException e) {
            log.error("验证码图片生成失败", e);
            throw new IllegalStateException("验证码图片生成失败", e);
        }
        stringRedisTemplate.opsForValue().set(redisPrefix + challengeId, answerStr, expireSeconds, TimeUnit.SECONDS);
        return new CaptchaVO(challengeId, Base64.getEncoder().encodeToString(png));
    }

    /**
     * 校验验证码答案并一次性消费
     *
     * @param challengeId 创建时下发的挑战 id
     * @param answer      用户输入的计算结果
     * @return true=答案正确（挑战已被消费，仅此一次有效）；false=错误/过期/重放/Redis 不可达
     */
    public boolean verify(String challengeId, String answer) {
        if (StrUtil.isBlank(challengeId) || StrUtil.isBlank(answer)) {
            return false;
        }
        try {
            String stored = stringRedisTemplate.execute(CONSUME_SCRIPT, List.of(redisPrefix + challengeId));
            if (StrUtil.isBlank(stored)) {
                log.warn("验证码校验失败: challengeId={} 不存在或已被消费（防重放）", challengeId);
                return false;
            }
            boolean ok = stored.equals(answer.trim());
            if (!ok) {
                log.warn("验证码答案错误: challengeId={}", challengeId);
            }
            return ok;
        } catch (DataAccessException e) {
            log.error("验证码校验访问 Redis 失败，拒绝注册（fail-closed）. challengeId={}", challengeId, e);
            return false;
        }
    }

    /** 随机 hex 挑战 id（SecureRandom 32 位，不可预测，防提前预取验证码撞答案） */
    private String newChallengeId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** 绘制算术题 PNG：浅灰底 + 噪点 + 干扰线 + 逐字符随机旋转/颜色，抗简单 OCR 且保持可读 */
    private byte[] renderPng(String text) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(244, 244, 244));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            // 噪点
            for (int i = 0; i < 90; i++) {
                g.setColor(new Color(randomInt(0, 200), randomInt(0, 200), randomInt(0, 200)));
                g.fillRect(secureRandom.nextInt(WIDTH), secureRandom.nextInt(HEIGHT), 1, 1);
            }
            // 干扰线
            for (int i = 0; i < 3; i++) {
                g.setColor(new Color(randomInt(0, 200), randomInt(0, 200), randomInt(0, 200)));
                g.drawLine(secureRandom.nextInt(WIDTH), secureRandom.nextInt(HEIGHT),
                        secureRandom.nextInt(WIDTH), secureRandom.nextInt(HEIGHT));
            }
            // 逐字符绘制
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int baseline = (HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
            int x = 8;
            for (char c : text.toCharArray()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(randomInt(20, 120), randomInt(20, 120), randomInt(20, 120)));
                int charWidth = fm.charWidth(c);
                g2.rotate((secureRandom.nextDouble() - 0.5) * 0.3, x + charWidth / 2.0, baseline);
                g2.drawString(String.valueOf(c), x, baseline);
                g2.dispose();
                x += charWidth + 2;
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
