package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求
 *
 * @author pulinsenz
 */
@Data
public class LoginRequest implements Serializable {

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    private static final long serialVersionUID = 1L;
}
