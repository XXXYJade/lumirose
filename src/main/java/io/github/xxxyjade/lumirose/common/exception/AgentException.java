package io.github.xxxyjade.lumirose.common.exception;

public class AgentException extends LumiRoseException {
    public AgentException(LumiRoseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AgentException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
