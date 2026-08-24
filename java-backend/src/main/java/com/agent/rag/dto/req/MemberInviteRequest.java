package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 邀请协作者请求（仅作者可操作；userId 与 userAccount 二选一）
 *
 * @author pulinsenz
 */
@Data
public class MemberInviteRequest implements Serializable {

    /**
     * 目标用户 id（与 userAccount 二选一）
     */
    private Long userId;

    /**
     * 目标用户账号（与 userId 二选一）
     */
    private String userAccount;

    private static final long serialVersionUID = 1L;
}
