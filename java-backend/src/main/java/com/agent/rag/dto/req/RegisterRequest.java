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
     * 算术验证码挑战 id（注册前先 GET /auth/captcha 获取）
     */
    private String captchaId;

    /**
     * 算术验证码答案（用户输入的计算结果，一次性校验，错误/过期需重新获取）
     */
    private String captchaAnswer;

    private static final long serialVersionUID = 1L;
}
