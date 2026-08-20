package com.agent.rag.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应：token + 用户信息
 *
 * @author pulinsenz
 */
@Data
@AllArgsConstructor
public class LoginResponse implements Serializable {

    /**
     * JWT token（请求头 Authorization: Bearer xxx）
     */
    private String token;

    /**
     * 用户信息
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;
}
