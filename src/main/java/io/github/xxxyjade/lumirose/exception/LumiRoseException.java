package io.github.xxxyjade.lumirose.exception;

import java.util.Objects;

/**
 * 框架异常基类。
 */
public class LumiRoseException extends RuntimeException {

    private final ErrorCode errorCode;

    public LumiRoseException(ErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode).message(), null);
    }

    public LumiRoseException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public LumiRoseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
