package io.github.xxxyjade.lumirose.exception;

import java.util.Objects;

/**
 * 框架封装异常类
 */
public class LumiRoseException extends RuntimeException {

    /**
     * 错误码
     */
    private final ErrorCode errorCode;

    protected LumiRoseException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).message());
        this.errorCode = errorCode;
    }

    protected LumiRoseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    protected LumiRoseException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode).message(), cause);
        this.errorCode = errorCode;
    }

    protected LumiRoseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public ErrorCode getErrorCode() {
        return this.errorCode;
    }
}