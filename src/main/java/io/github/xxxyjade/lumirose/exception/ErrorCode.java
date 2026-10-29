package io.github.xxxyjade.lumirose.exception;

/**
 * 错误码接口
 */
public interface ErrorCode {

    /**
     * @return 错误码: int
     */
    int code();

    /**
     * @return 错误消息: String
     */
    String message();
}