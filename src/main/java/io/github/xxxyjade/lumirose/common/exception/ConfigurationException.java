package io.github.xxxyjade.lumirose.common.exception;

public class ConfigurationException extends LumiRoseException {
    public ConfigurationException(LumiRoseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
