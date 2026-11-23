package io.github.xxxyjade.lumirose.common.exception;

/**
 * 框架统一错误码。
 *
 * <p>码段划分：1xxx 配置与参数，2xxx 模型客户端，3xxx Agent，
 * 4xxx 工具，5xxx 仓储，9xxx 内部错误。每个错误码对应一个异常类型，
 * 见 {@link LumiRoseException} 及其子类。
 */
public enum LumiRoseErrorCode {
    // 配置与参数
    CONFIG_INVALID(1001, "配置非法"),
    PARAM_INVALID(1002, "参数非法"),
    // 模型客户端
    CLIENT_HTTP_ERROR(2001, "模型请求失败"),
    CLIENT_RESPONSE_INVALID(2002, "模型响应非法"),
    CLIENT_IO_ERROR(2003, "网络请求失败"),
    // Agent
    AGENT_TURN_LIMIT(3001, "轮次用尽"),
    AGENT_OUTPUT_TRUNCATED(3002, "模型输出被截断"),
    AGENT_RESPONSE_INVALID(3003, "模型响应语义非法"),
    AGENT_CANCELLED(3004, "运行已取消"),
    AGENT_COMPRESSION_FAILED(3005, "上下文压缩失败"),
    // 工具
    TOOL_NOT_FOUND(4001, "工具未注册"),
    TOOL_ARGS_INVALID(4002, "工具参数非法"),
    TOOL_EXECUTION_FAILED(4003, "工具执行失败"),
    TOOL_PATH_INVALID(4004, "路径非法"),
    TOOL_FILE_NOT_FOUND(4005, "文件不存在"),
    TOOL_FILE_EXISTS(4006, "文件已存在"),
    TOOL_EDIT_NO_MATCH(4007, "编辑目标未匹配"),
    TOOL_EDIT_NOT_UNIQUE(4008, "编辑目标不唯一"),
    // 仓储
    STORAGE_FAILED(5001, "仓储操作失败"),
    INTERNAL_ERROR(9001, "内部错误");

    private final int code;
    private final String defaultMessage;

    LumiRoseErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回对外错误码。
     *
     * @return 整数错误码
     */
    public int code() {
        return code;
    }

    /**
     * 返回默认中文提示。
     *
     * @return 默认提示文本
     */
    public String defaultMessage() {
        return defaultMessage;
    }
}
