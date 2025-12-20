package com.dream.codegenerate.exception;

import lombok.Getter;
/**
 * 自定义业务异常
 * <p>
 * 职责：Service 层手动抛出的逻辑错误载体。
 * 它可以只传 ErrorCode，也可以覆盖 ErrorCode 的默认 message。
 * </p>
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
