package io.github.xxxyjade.lumirose.common.exception;

/**
 * 仓储读写或 DO/Entity 转换失败。
 */
public class StorageException extends LumiRoseException {
    public StorageException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
