package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

public interface Tool {

    @JsonProperty("name")
    String name();

    @JsonProperty("description")
    String description();

    @JsonProperty("parameters")
    ToolParameters parameters();

    Class<? extends ToolParameters> parametersClass();

    String execute(ToolParameters parameters) throws IOException;

    static Tool of(
            String name,
            String description,
            ToolParameters parameters,
            Class<? extends ToolParameters> parametersClass,
            Function<ToolParameters, String> handler
    ) {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(parameters, "parameters is required");
        Objects.requireNonNull(handler, "handler is required");

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
            public String execute(ToolParameters parameters) {
                return handler.apply(parameters);
            }
        };
    }
}
