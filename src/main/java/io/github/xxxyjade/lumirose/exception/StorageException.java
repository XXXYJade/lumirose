package io.github.xxxyjade.lumirose.exception;

/**
 * 仓储读写错误。
 */
public class StorageException extends LumiRoseException {

    public StorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public StorageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public StorageException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
