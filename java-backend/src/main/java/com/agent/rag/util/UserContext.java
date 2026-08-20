package com.agent.rag.util;

import com.agent.rag.entity.User;

/**
 * 当前登录用户上下文（ThreadLocal）
 *
 * @author pulinsenz
 */
public class UserContext {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    public static void setUser(User user) {
        USER_HOLDER.set(user);
    }

    public static User getUser() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
