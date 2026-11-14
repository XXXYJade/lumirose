package io.github.xxxyjade.lumirose.exception;

/**
 * 工具层错误。{@link #toolName()} 记录出错的工具，可能为 null。
 */
public class ToolException extends LumiRoseException {

    private final String toolName;

    public ToolException(ErrorCode errorCode, String message) {
        this(errorCode, null, message, null);
    }

    public ToolException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, null, message, cause);
    }

    public ToolException(ErrorCode errorCode, String toolName, String message) {
        this(errorCode, toolName, message, null);
    }

    public ToolException(ErrorCode errorCode, String toolName, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }
}
