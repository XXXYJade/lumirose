package io.github.xxxyjade.lumirose.exception;

/**
 * 错误码枚举类
 */
public enum LumiRoseErrorCode implements ErrorCode {
    PARAM_INVALID(1001, "参数校验失败"),
    BODY_INVALID(1002, "请求体解析失败"),
    UPLOAD_TOO_LARGE(1003, "上传内容超过大小限制"),
    NOT_FOUND(1004, "接口不存在"),
    METHOD_NOT_ALLOWED(1005, "方法不支持"),
    SESSION_NOT_FOUND(2001, "会话不存在"),
    DOCUMENT_NOT_FOUND(3001, "文档不存在"),
    CHUNK_NOT_FOUND(3002, "chunk 不存在"),
    BACKUP_NOT_FOUND(3003, "备份库路径不存在"),
    PATH_INVALID(3004, "备份库路径校验失败"),
    CHAT_MODEL_NOT_CONFIGURED(4001, "聊天模型未配置"),
    CHAT_MODEL_CALL_FAILED(4002, "聊天模型调用失败"),
    EMBEDDING_MODEL_NOT_CONFIGURED(4003, "Embedding 模型未配置"),
    EMBEDDING_MODEL_CALL_FAILED(4004, "Embedding 模型调用失败"),
    CONFIG_INVALID(5001, "配置校验失败"),
    CONFIG_IO_FAILED(5002, "配置文件读写失败"),
    DATABASE_FAILED(9001, "数据库操作失败"),
    BACKUP_IO_FAILED(9002, "备份库文件 IO 失败"),
    INTERNAL_ERROR(9999, "服务器内部错误");

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