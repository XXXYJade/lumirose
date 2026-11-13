package io.github.xxxyjade.lumirose.tool.args;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ReadToolParameters.Builder.class)
public class ReadToolParameters implements ToolParameters {

    @JsonProperty("path")
    @JsonPropertyDescription("相对工作区的文件路径，例如 src/main/java/Foo.java")
    private final String path;

//    @JsonPropertyDescription("起始行号，从 1 开始")
//    private final int offset;
//
//    @JsonPropertyDescription("最多读取多少行，1-2000")
//    private final int limit;

    private ReadToolParameters(Builder builder) {
        this.path = builder.path;
//        this.offset = builder.offset;
//        this.limit = builder.limit;
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
//        private int offset;
//        private int limit;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

//        public Builder offset(int offset) {
//            this.offset = offset;
//            return this;
//        }

//        public Builder limit(int limit) {
//            this.limit = limit;
//            return this;
//        }

        public ReadToolParameters build(){
            return new ReadToolParameters(this);
        }
    }
}
