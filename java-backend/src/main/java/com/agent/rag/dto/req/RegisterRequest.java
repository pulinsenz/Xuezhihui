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

    private static final long serialVersionUID = 1L;
}
