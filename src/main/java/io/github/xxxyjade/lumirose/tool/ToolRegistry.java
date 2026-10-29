package io.github.xxxyjade.lumirose.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, Class<? extends Tool>> tools;

    private ToolRegistry(){
        this.tools = new HashMap<>();
    }

    private ToolRegistry(Builder builder) {
        this.tools = builder.tools;
    }

    public List<Class<? extends Tool>> getTools() {
        return new ArrayList<>(tools.values());
    }

    public Class<? extends Tool> getTool(String key) {
        return tools.get(key);
    }

    public static Builder builder(){
        return new Builder();
    }

    public static final class Builder {
        private Map<String, Class<? extends Tool>> tools = new HashMap<>();

        public Builder addTool(Class<? extends Tool> tool) {
            this.tools.put(tool.getSimpleName(), tool);
            return this;
        }

        public Builder addTools(List<Class<? extends Tool>> tools) {
            for (Class<? extends Tool> tool : tools) {
                this.tools.put(tool.getSimpleName(), tool);
            }
            return this;
        }

        public ToolRegistry build() {
            return new ToolRegistry(this);
        }
    }
}
