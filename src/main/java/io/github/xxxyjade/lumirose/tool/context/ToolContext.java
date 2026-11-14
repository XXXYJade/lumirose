package io.github.xxxyjade.lumirose.tool.context;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.LumiRoseException;
import io.github.xxxyjade.lumirose.exception.ToolException;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表与执行入口。
 */
public final class ToolContext {

    private final Map<String, Tool> tools;
    private final ObjectMapper mapper;

    private ToolContext(Builder builder) {
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tools));
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 注册顺序的工具快照。
     */
    public List<Tool> tools() {
        return List.copyOf(tools.values());
    }

    /**
     * 按名称执行工具，参数为模型返回的 JSON 字符串。
     */
    public String execute(String name, String arguments) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolException(LumiRoseErrorCode.TOOL_NOT_FOUND, name, "未注册的工具: " + name);
        }

        String json = (arguments == null || arguments.isBlank()) ? "{}" : arguments;

        ToolParameters parameters;
        try {
            parameters = mapper.readValue(json, tool.parametersClass());
        } catch (IOException e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_ARGS_INVALID, name, "工具参数解析失败: " + name, e);
        }

        try {
            return tool.execute(parameters);
        } catch (LumiRoseException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException(LumiRoseErrorCode.TOOL_EXECUTION_FAILED, name, "工具执行失败: " + name, e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, Tool> tools = new LinkedHashMap<>();

        public Builder register(Tool tool) {
            if (tool == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "tool 不能为空");
            }
            if (tools.containsKey(tool.name())) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "工具重名: " + tool.name());
            }
            tools.put(tool.name(), tool);
            return this;
        }

        public Builder registerAll(List<Tool> tools) {
            if (tools == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "tools 不能为空");
            }
            tools.forEach(this::register);
            return this;
        }

        public ToolContext build() {
            return new ToolContext(this);
        }
    }
}
