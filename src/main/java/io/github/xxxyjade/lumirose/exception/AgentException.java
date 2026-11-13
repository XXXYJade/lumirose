package io.github.xxxyjade.lumirose.exception;

/**
 * 智能体运行错误。
 */
public class AgentException extends LumiRoseException {

    public AgentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AgentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AgentException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
