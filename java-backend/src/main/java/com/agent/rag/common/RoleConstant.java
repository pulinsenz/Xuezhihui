package com.agent.rag.common;

/**
 * 用户角色常量
 *
 * @author pulinsenz
 */
public final class RoleConstant {

    /**
     * 普通用户
     */
    public static final String USER = "user";

    /**
     * 管理员
     */
    public static final String ADMIN = "admin";

    /** 超级管理员 */
    public static final String SUPER_ADMIN = "super_admin";

    /** 系统保留的初始管理员账号 */
    public static final String BUILT_IN_ADMIN_ACCOUNT = "admin";

    public static boolean isAdministrator(String role) {
        return ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }

    public static boolean satisfies(String actualRole, String requiredRole) {
        return ADMIN.equals(requiredRole) ? isAdministrator(actualRole) : requiredRole.equals(actualRole);
    }

    private RoleConstant() {
    }
}
