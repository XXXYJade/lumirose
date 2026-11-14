package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.util.function.Function;

/**
 * 可供模型调用的工具。
 */
public interface Tool {

    @JsonProperty("name")
    String name();

    @JsonProperty("description")
    String description();

    @JsonProperty("parameters")
    ToolParameters parameters();

    Class<? extends ToolParameters> parametersClass();

    /**
     * 执行工具；失败时抛出运行时异常，不声明受检异常。
     */
    String execute(ToolParameters parameters);

    /**
     * 创建轻量工具。参数类由 {@code parameters.getClass()} 推导。
     */
    static Tool of(
            String name,
            String description,
            ToolParameters parameters,
            Function<ToolParameters, String> handler
    ) {
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
