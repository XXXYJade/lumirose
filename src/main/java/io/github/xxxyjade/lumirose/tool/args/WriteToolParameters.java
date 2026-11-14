package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * {@code write_file} 的参数。
 */
@JsonDeserialize(builder = WriteToolParameters.Builder.class)
public class WriteToolParameters implements ToolParameters {

    @JsonProperty(value = "path", required = true)
    @JsonPropertyDescription("相对工作区的文件路径")
    private final String path;

    @JsonProperty(value = "content", required = true)
    @JsonPropertyDescription("要写入的 UTF-8 文本")
    private final String content;

    @JsonProperty("overwrite")
    @JsonPropertyDescription("目标已存在时是否覆盖，默认 false")
    private final boolean overwrite;

    private WriteToolParameters(Builder builder) {
        this.path = builder.path;
        this.content = builder.content;
        this.overwrite = builder.overwrite;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String path() {
        return this.path;
    }

    public String content() {
        return this.content;
    }

    public boolean overwrite() {
        return this.overwrite;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String path;
        private String content;
        private boolean overwrite;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        public WriteToolParameters build() {
            return new WriteToolParameters(this);
        }
    }
}
