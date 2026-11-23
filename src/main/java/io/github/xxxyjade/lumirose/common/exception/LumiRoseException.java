package io.github.xxxyjade.lumirose.common.exception;

/**
 * 框架异常基类，携带稳定的 {@link LumiRoseErrorCode}。
 *
 * <p>异常消息只用于描述错误类别和上下文，不包含 API Key、消息内容、
 * 工具参数或请求/响应体。
 */
public class LumiRoseException extends RuntimeException {
    private final LumiRoseErrorCode errorCode;

    /**
     * 创建不带 cause 的框架异常。
     *
     * @param errorCode 错误码
     * @param message   对外消息
     */
    public LumiRoseException(LumiRoseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建保留 cause 的框架异常。
     *
     * @param errorCode 错误码
     * @param message   对外消息
     * @param cause     原始异常
     */
    public LumiRoseException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 返回错误码。
     *
     * @return 稳定错误码
     */
    public LumiRoseErrorCode getErrorCode() {
        return errorCode;
    }
}
