package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码请求
 *
 * @author pulinsenz
 */
@Data
public class ChangePasswordRequest implements Serializable {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
