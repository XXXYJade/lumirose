package io.github.xxxyjade.lumirose.common.exception;

public class ToolException extends LumiRoseException {
    private final String toolName;

    public ToolException(LumiRoseErrorCode errorCode, String toolName, String message) {
        super(errorCode, message);
        this.toolName = toolName;
    }

    public ToolException(LumiRoseErrorCode errorCode, String toolName, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }
}
