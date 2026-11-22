package io.github.xxxyjade.lumirose.common.exception;

public class LumiRoseException extends RuntimeException {
    private final LumiRoseErrorCode errorCode;

    public LumiRoseException(LumiRoseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LumiRoseException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public LumiRoseErrorCode getErrorCode() {
        return errorCode;
    }
}
