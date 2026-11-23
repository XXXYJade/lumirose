package io.github.xxxyjade.lumirose.common.exception;

/**
 * 模型客户端错误。
 *
 * <p>HTTP 状态码只用于决定是否可重试：网络错误、429 和 5xx 可重试，
 * 其余 4xx 不重试。异常消息不包含供应商原始错误消息。
 */
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

    /**
     * 判断当前错误是否值得重试。
     *
     * @return 网络错误、429 或 5xx 返回 {@code true}
     */
    public boolean retriable() {
        return getErrorCode() == LumiRoseErrorCode.CLIENT_IO_ERROR
                || (httpStatus != null && (httpStatus == 429 || httpStatus >= 500));
    }

    /**
     * 返回 HTTP 状态码；非 HTTP 错误返回 {@code null}。
     *
     * @return HTTP 状态码或 {@code null}
     */
    public Integer httpStatus() {
        return httpStatus;
    }
}
