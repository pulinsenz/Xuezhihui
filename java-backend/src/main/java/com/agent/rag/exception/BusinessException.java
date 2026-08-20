package com.agent.rag.exception;

import com.agent.rag.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常，携带错误码
 *
 * @author pulinsenz
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
