package io.github.xxxyjade.lumirose.exception;

/**
 * 框架错误码。
 */
public enum LumiRoseErrorCode implements ErrorCode {

    CONFIG_INVALID(1001, "配置缺失或非法"),
    PARAM_INVALID(1002, "运行参数非法"),

    CLIENT_HTTP_ERROR(2001, "模型请求失败"),
    CLIENT_RESPONSE_INVALID(2002, "模型响应结构非法"),
    CLIENT_IO_ERROR(2003, "模型请求网络异常"),

    AGENT_TURN_LIMIT(3001, "轮次用尽"),
    AGENT_OUTPUT_TRUNCATED(3002, "模型输出被截断"),
    AGENT_RESPONSE_INVALID(3003, "模型响应语义非法"),
    AGENT_CANCELLED(3004, "运行已取消"),

    TOOL_NOT_FOUND(4001, "未注册的工具"),
    TOOL_ARGS_INVALID(4002, "工具参数非法"),
    TOOL_EXECUTION_FAILED(4003, "工具执行失败"),
    TOOL_PATH_INVALID(4004, "路径非法"),
    TOOL_FILE_NOT_FOUND(4005, "文件不存在"),
    TOOL_FILE_EXISTS(4006, "文件已存在"),
    TOOL_EDIT_NO_MATCH(4007, "编辑目标未匹配"),
    TOOL_EDIT_NOT_UNIQUE(4008, "编辑目标多处匹配"),

    STORAGE_FAILED(5001, "仓储读写失败"),

    INTERNAL_ERROR(9001, "内部错误");

    private final int code;
    private final String message;

    LumiRoseErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
