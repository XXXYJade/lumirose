package io.github.xxxyjade.lumirose.common.exception;

/**
 * 工具错误，保留出错工具名以便调用方定位。
 */
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

    /**
     * 返回出错工具名。
     *
     * @return 工具名
     */
    public String toolName() {
        return toolName;
    }
}
