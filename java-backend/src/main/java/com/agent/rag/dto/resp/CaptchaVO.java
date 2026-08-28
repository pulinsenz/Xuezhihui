package com.agent.rag.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 注册算术验证码响应
 *
 * @author pulinsenz
 */
@Data
@AllArgsConstructor
public class CaptchaVO {

    /**
     * 验证码挑战 id（注册时原样回传，服务端凭其定位一次性答案）
     */
    private String challengeId;

    /**
     * 验证码图片 base64（前端拼 data:image/png;base64, 前缀渲染）
     */
    private String imageBase64;
}
