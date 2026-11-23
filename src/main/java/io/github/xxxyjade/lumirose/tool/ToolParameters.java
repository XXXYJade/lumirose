package io.github.xxxyjade.lumirose.tool;

/**
 * 工具参数的标记接口。
 *
 * <p>实现类应声明为 {@code public static} 类并使用 public 字段，
 * 字段通过 {@link com.fasterxml.jackson.annotation.JsonProperty} 暴露给模型；
 * {@link ToolSchemaFactory} 和 {@link ToolContext} 都基于这些字段工作。
 */
public interface ToolParameters {
}
