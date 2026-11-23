package io.github.xxxyjade.lumirose.common.exception;

/**
 * 配置、工具定义或压缩参数非法。
 */
public class ConfigurationException extends LumiRoseException {
    public ConfigurationException(LumiRoseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
