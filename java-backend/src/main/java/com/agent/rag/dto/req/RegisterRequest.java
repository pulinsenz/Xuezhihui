package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 注册请求
 *
 * @author pulinsenz
 */
@Data
public class RegisterRequest implements Serializable {

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

    /**
     * 用户昵称（可选，默认取账号）
     */
    private String userName;

    /**
     * Cloudflare Turnstile 人机验证 token（生产配置 TURNSTILE_SECRET_KEY 后必填）
     */
    private String turnstileToken;

    private static final long serialVersionUID = 1L;
}
