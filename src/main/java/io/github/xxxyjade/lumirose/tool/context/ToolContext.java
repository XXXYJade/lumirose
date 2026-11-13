package io.github.xxxyjade.lumirose.tool.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolContext {

    private final Map<String, Tool> tools;
    private final ObjectMapper mapper = new ObjectMapper();

    private ToolContext(){
        this.tools = new HashMap<>();
    }

    private ToolContext(Builder builder) {
        this.tools = builder.tools;
    }

    public List<Tool> tools() {
        return new ArrayList<>(tools.values());
    }

    public String execute(String key, String arguments) {
        Tool tool = this.tools.get(key);
        try {
            ToolParameters parameters = mapper.readValue(arguments, tool.parametersClass());
            return tool.execute(parameters);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String execute(String key, ToolParameters parameters) {
        Tool tool = this.tools.get(key);
        try {
            return tool.execute(parameters);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Builder builder(){
        return new Builder();
    }

    public static final class Builder {
        private Map<String, Tool> tools = new HashMap<>();

        public Builder register(Tool tool){
            this.tools.putIfAbsent(tool.name(), tool);
            return this;
        }

        public Builder registerAll(List<Tool> tools){
            tools.forEach(this::register);
            return this;
        }

        public ToolContext build() {
            return new ToolContext(this);
        }
    }
}
