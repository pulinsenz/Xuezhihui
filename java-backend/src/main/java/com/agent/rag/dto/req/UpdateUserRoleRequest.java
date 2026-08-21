package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改用户角色请求
 *
 * @author pulinsenz
 */
@Data
public class UpdateUserRoleRequest implements Serializable {

    /**
     * 目标角色：user / admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}
