package io.github.xxxyjade.lumirose.common.result;

/**
 * 一次 Agent 运行的结果。
 *
 * <p>结果为只读视图；正文、token 用量和增量流都描述同一次运行。
 * 取消通过 {@link #cancel()} 触发，不通过 {@code close()}。
 */
public interface LumiRoseResult {
    /**
     * 返回所有 assistant 正文的拼接。
     *
     * <p>包含工具调用轮次的正文前言；不包含 reasoning 内容和工具结果。
     *
     * @return 最终正文，可能为空字符串
     */
    String content();

    /**
     * 返回本次运行的 prompt token 总数。
     *
     * @return prompt token 数
     */
    int promptTokens();

    /**
     * 返回本次运行的 completion token 总数。
     *
     * @return completion token 数
     */
    int completionTokens();

    /**
     * 返回只包含 assistant 正文的增量流。
     *
     * <p>不包含 reasoning、tool call 或轮次事件；实现可以只允许迭代一次。
     *
     * @return 正文增量
     */
    Iterable<String> deltas();

    /**
     * 请求取消本次运行。
     *
     * <p>取消后不再产生新的增量，运行以取消错误结束。
     */
    void cancel();
}
