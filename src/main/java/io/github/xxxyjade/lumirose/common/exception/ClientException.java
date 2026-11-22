package io.github.xxxyjade.lumirose.common.exception;

public class ClientException extends LumiRoseException {
    private final Integer httpStatus;

    public ClientException(LumiRoseErrorCode errorCode, String message) {
        this(errorCode, null, message, null);
    }

    public ClientException(LumiRoseErrorCode errorCode, int httpStatus, String message) {
        this(errorCode, Integer.valueOf(httpStatus), message, null);
    }

    public ClientException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, null, message, cause);
    }

    private ClientException(LumiRoseErrorCode errorCode, Integer httpStatus, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.httpStatus = httpStatus;
    }

    public boolean retriable() {
        return getErrorCode() == LumiRoseErrorCode.CLIENT_IO_ERROR
                || (httpStatus != null && (httpStatus == 429 || httpStatus >= 500));
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
