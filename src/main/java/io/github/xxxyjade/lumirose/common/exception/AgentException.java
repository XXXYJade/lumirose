package io.github.xxxyjade.lumirose.common.exception;

/**
 * Agent 运行期错误：参数、轮次、输出截断、取消或压缩失败。
 */
public class AgentException extends LumiRoseException {
    public AgentException(LumiRoseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AgentException(LumiRoseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
