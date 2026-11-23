package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.common.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;

import java.util.function.Function;

/**
 * 可被模型调用的工具。
 *
 * <p>三个与参数有关的方法必须保持一致：{@link #parameters()} 提供默认值，
 * {@link #parametersClass()} 决定模型参数的反序列化类型，
 * {@link ToolSchemaFactory} 依据同一个类生成 schema。三者不一致时，
 * 模型看到的参数形状会与实际解析结果不符。
 *
 * <p>实现应无状态或线程安全；返回的字符串会作为工具消息内容回填给模型。
 */
public interface Tool {

    /**
     * 返回工具名，必须与注册名以及模型调用中的 {@code function.name} 一致。
     *
     * @return 工具名
     */
    String name();

    /**
     * 返回给模型阅读的工具说明。
     *
     * @return 工具说明
     */
    String description();

    /**
     * 返回参数默认值实例，同时决定 {@link #parametersClass()}。
     *
     * @return 参数实例
     */
    ToolParameters parameters();

    /**
     * 返回用于反序列化模型参数的参数类型。
     *
     * @return 参数类型
     */
    Class<? extends ToolParameters> parametersClass();

    /**
     * 执行工具。
     *
     * @param parameters 已按 {@link #parametersClass()} 反序列化的参数
     * @return 工具结果文本
     */
    String execute(ToolParameters parameters);

    /**
     * 用 lambda 创建轻量工具。
     *
     * <p>参数类型直接取自 {@code parameters.getClass()}，因此传入的实例
     * 必须是无状态可共享的默认值对象。
     *
     * @param name        工具名
     * @param description 给模型看的说明
     * @param parameters  参数默认值，不能为空
     * @param handler     执行逻辑
     * @return 工具实例
     * @throws ConfigurationException 任一参数为 {@code null}
     */
    static Tool of(String name, String description, ToolParameters parameters,
                   Function<ToolParameters, String> handler) {
        if (name == null) {
            throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "name 不能为空");
        }
        if (description == null) {
            throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "description 不能为空");
        }
        if (parameters == null) {
            throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "parameters 不能为空");
        }
        if (handler == null) {
            throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "handler 不能为空");
        }
        Class<? extends ToolParameters> parametersClass = parameters.getClass();
        return new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public ToolParameters parameters() {
                return parameters;
            }

            @Override
            public Class<? extends ToolParameters> parametersClass() {
                return parametersClass;
            }

            @Override
            public String execute(ToolParameters arguments) {
                return handler.apply(arguments);
            }
        };
    }
}
