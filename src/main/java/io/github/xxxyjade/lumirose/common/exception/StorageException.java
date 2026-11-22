package io.github.xxxyjade.lumirose.common.exception;

public class StorageException extends LumiRoseException {
    public StorageException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
