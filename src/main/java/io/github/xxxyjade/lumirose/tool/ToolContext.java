package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.common.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.common.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.common.exception.ToolException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表与执行入口。
 *
 * <p>注册顺序即 {@link #tools()} 的返回顺序，注册表构建完成后不可修改。
 * {@link #execute(String, String)} 负责把模型返回的原始 JSON 解析成
 * {@link ToolParameters}，并把参数错误、未知工具和工具内部异常统一映射为
 * {@link ToolException}；工具实现只需处理类型化参数。
 *
 * <p>实例本身不可变，可安全地被多个请求共享；但工具实现自身仍需线程安全。
 */
public final class ToolContext {
    private final Map<String, Tool> tools;
    private final ObjectMapper mapper;

    private ToolContext(Builder builder) {
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tools));
        // 模型偶尔会附带 schema 未声明的字段；忽略它们比拒绝整个调用更宽容。
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 返回已注册工具，顺序与注册顺序一致。
     *
     * @return 不可变工具列表
     */
    public List<Tool> tools() {
        return List.copyOf(tools.values());
    }

    /**
     * 解析参数并执行指定工具。
     *
     * @param name      工具名
     * @param arguments 模型返回的 JSON 参数；{@code null} 或空白按 {@code {}} 处理
     * @return 工具结果文本
     * @throws ToolException 工具未注册、参数不是合法 JSON，或工具执行失败
     */
    public String execute(String name, String arguments) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolException(LumiRoseErrorCode.TOOL_NOT_FOUND, name, "未注册的工具: " + name);
        }
        // 模型可能省略 arguments；空参数与空对象等价。
        String json = arguments == null || arguments.isBlank() ? "{}" : arguments;
        ToolParameters parameters;
        try {
            parameters = mapper.readValue(json, tool.parametersClass());
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, name, "工具参数解析失败", e);
        }
        if (parameters == null) {
            // 字面量 null 能通过 Jackson 解析，但它不是合法的参数对象。
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, name, "工具参数不能为 null");
        }
        try {
            return tool.execute(parameters);
        } catch (LumiRoseException e) {
            // 工具已经给出稳定错误码，原样透传，避免被包装成笼统的 4003。
            throw e;
        } catch (Exception e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, name, "工具执行失败", e);
        }
    }

    /**
     * 创建注册表构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 工具注册表构建器；每次注册都会立即校验，构建后副本与外部修改隔离。
     */
    public static final class Builder {
        private final Map<String, Tool> tools = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 注册一个工具。
         *
         * @param tool 工具实例
         * @return 当前构建器
         * @throws ConfigurationException 工具为 {@code null} 或名称重复
         */
        public Builder register(Tool tool) {
            if (tool == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "tool 不能为空");
            }
            if (tools.containsKey(tool.name())) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID,
                        "工具重名: " + tool.name());
            }
            tools.put(tool.name(), tool);
            return this;
        }

        /**
         * 按顺序批量注册工具。
         *
         * @param tools 工具集合
         * @return 当前构建器
         * @throws ConfigurationException 集合为 {@code null}，或其中任一项非法
         */
        public Builder registerAll(Collection<Tool> tools) {
            if (tools == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "tools 不能为空");
            }
            tools.forEach(this::register);
            return this;
        }

        /**
         * 构建不可变的注册表。
         *
         * @return 工具上下文
         */
        public ToolContext build() {
            return new ToolContext(this);
        }
    }
}
