package com.agent.rag.common;

import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author pulinsenz
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    ACCOUNT_EXIST(40001, "账号已存在"),
    ACCOUNT_OR_PASSWORD_ERROR(40002, "账号或密码错误"),
    LOGIN_LOCKED(40003, "登录失败次数过多，账号已锁定"),
    LOGIN_TOO_FREQUENT(40004, "请求过于频繁，请稍后再试"),
    CHAT_TOO_FREQUENT(40005, "对话过于频繁，请稍后再试"),
    NOT_LOGIN(40100, "未登录"),
    NO_AUTH(40101, "无权限"),
    NOT_FOUND(40400, "请求数据不存在"),
    USER_NOT_EXIST(40401, "用户不存在"),
    OPERATION_ERROR(50001, "操作失败"),
    SYSTEM_ERROR(50000, "系统内部异常");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
