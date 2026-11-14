package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * {@code delete_file} 的参数。
 */
@JsonDeserialize(builder = DeleteToolParameters.Builder.class)
public class DeleteToolParameters implements ToolParameters {

    @JsonProperty(value = "path", required = true)
    @JsonPropertyDescription("相对工作区的文件路径")
    private final String path;

    private DeleteToolParameters(Builder builder) {
        this.path = builder.path;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String path() {
        return this.path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String path;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public DeleteToolParameters build() {
            return new DeleteToolParameters(this);
        }
    }
}
