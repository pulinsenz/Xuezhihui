package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新个人资料请求（昵称/头像/简介）
 * <p>
 * 仅允许更新这三个字段，角色/密码等敏感字段不可经此修改（防越权）。
 *
 * @author pulinsenz
 */
@Data
public class UpdateProfileRequest implements Serializable {

    /**
     * 昵称（1-30 字，必填）
     */
    private String userName;

    /**
     * 头像 URL（由 /auth/avatar 上传返回，或留空）
     */
    private String userAvatar;

    /**
     * 个人简介（≤200 字，可留空）
     */
    private String userProfile;

    private static final long serialVersionUID = 1L;
}
