package io.github.xxxyjade.lumirose.exception;

/**
 * 模型客户端错误。{@link #httpStatus()} 为 null 表示不是 HTTP 层错误。
 */
public class ClientException extends LumiRoseException {

    private final Integer httpStatus;

    public ClientException(ErrorCode errorCode, String message) {
        this(errorCode, null, message, null);
    }

    public ClientException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, null, message, cause);
    }

    public ClientException(ErrorCode errorCode, int httpStatus, String message) {
        this(errorCode, Integer.valueOf(httpStatus), message, null);
    }

    public ClientException(ErrorCode errorCode, Integer httpStatus, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    /**
     * 该错误重跑整次 run 是否可能成功：限流、服务端错误和网络异常为 true。
     */
    public boolean retriable() {
        if (getErrorCode() == LumiRoseErrorCode.CLIENT_IO_ERROR) {
            return true;
        }
        return httpStatus != null && (httpStatus == 429 || httpStatus >= 500);
    }
}
