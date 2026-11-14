package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * {@code edit_file} 的参数。
 */
@JsonDeserialize(builder = EditToolParameters.Builder.class)
public class EditToolParameters implements ToolParameters {

    @JsonProperty(value = "path", required = true)
    @JsonPropertyDescription("相对工作区的文件路径")
    private final String path;

    @JsonProperty(value = "old_string", required = true)
    @JsonPropertyDescription("要替换的原文本，必须在文件中唯一出现")
    private final String oldString;

    @JsonProperty(value = "new_string", required = true)
    @JsonPropertyDescription("替换后的文本，允许为空字符串")
    private final String newString;

    private EditToolParameters(Builder builder) {
        this.path = builder.path;
        this.oldString = builder.oldString;
        this.newString = builder.newString;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String path() {
        return this.path;
    }

    public String oldString() {
        return this.oldString;
    }

    public String newString() {
        return this.newString;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String path;
        private String oldString;
        private String newString;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder oldString(String oldString) {
            this.oldString = oldString;
            return this;
        }

        public Builder newString(String newString) {
            this.newString = newString;
            return this;
        }

        public EditToolParameters build() {
            return new EditToolParameters(this);
        }
    }
}
