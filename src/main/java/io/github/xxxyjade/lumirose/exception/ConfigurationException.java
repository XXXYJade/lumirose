package io.github.xxxyjade.lumirose.exception;

/**
 * 配置或工具定义非法。
 */
public class ConfigurationException extends LumiRoseException {

    public ConfigurationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConfigurationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
