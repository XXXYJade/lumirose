package io.github.xxxyjade.lumirose.model.chat.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;

@JsonDeserialize(builder = ChatCompletionResponseParam.Builder.class)
public class ChatCompletionResponseParam {

    private final List<Choice> choices;
    private final Long created;
    private final String id;
    private final String model;
    private final String object;
    private final String systemFingerprint;
    private final Usage usage;

    private ChatCompletionResponseParam(Builder builder) {
        this.choices = builder.choices;
        this.created = builder.created;
        this.id = builder.id;
        this.model = builder.model;
        this.object = builder.object;
        this.systemFingerprint = builder.systemFingerprint;
        this.usage = builder.usage;
    }

    public List<Choice> choices() {
        return this.choices;
    }

    public Long created() {
        return this.created;
    }

    public String id() {
        return this.id;
    }

    public String model() {
        return this.model;
    }

    public String object() {
        return this.object;
    }

    public String systemFingerprint() {
        return this.systemFingerprint;
    }

    public Usage usage() {
        return this.usage;
    }

    @Override
    public String toString() {
        return "ChatCompletionResponseParam{" +
                "choices=" + choices +
                ", created=" + created +
                ", id='" + id + '\'' +
                ", model='" + model + '\'' +
                ", object='" + object + '\'' +
                ", systemFingerprint='" + systemFingerprint + '\'' +
                ", usage=" + usage +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private List<Choice> choices;
        private Long created;
        private String id;
        private String model;
        private String object;
        @JsonProperty("system_fingerprint") private String systemFingerprint;
        private Usage usage;

        public Builder choices(List<Choice> choices) {
            this.choices = choices;
            return this;
        }

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder systemFingerprint(String systemFingerprint) {
            this.systemFingerprint = systemFingerprint;
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public ChatCompletionResponseParam build() {
            return new ChatCompletionResponseParam(this);
        }
    }

    @JsonDeserialize(builder = ChatCompletionResponseParam.Choice.Builder.class)
    public static class Choice {
        private final Integer index;
        private final String finishReason;
        private final ChatCompletionMessage message;
        private final Object logprobs;

        public Choice(Builder builder) {
            this.index = builder.index;
            this.finishReason = builder.finishReason;
            this.message = builder.message;
            this.logprobs = builder.logprobs;
        }

        public Integer index() {
            return this.index;
        }

        public String finishReason() {
            return this.finishReason;
        }

        public ChatCompletionMessage message() {
            return this.message;
        }

        public Object logprobs() {
            return this.logprobs;
        }

        @Override
        public String toString() {
            return "Choice{" +
                    "index=" + index +
                    ", finishReason='" + finishReason + '\'' +
                    ", message=" + message +
                    ", logprobs=" + logprobs +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private Integer index;
            @JsonProperty("finish_reason") private String finishReason;
            private ChatCompletionMessage message;
            private Object logprobs;

            public Builder index(Integer index) {
                this.index = index;
                return this;
            }

            public Builder finishReason(String finishReason) {
                this.finishReason = finishReason;
                return this;
            }

            public Builder message(ChatCompletionMessage message) {
                this.message = message;
                return this;
            }

            public Builder logprobs(Object logprobs) {
                this.logprobs = logprobs;
                return this;
            }

            public Choice build() {
                return new Choice(this);
            }
        }
    }

    @JsonDeserialize(builder = ChatCompletionResponseParam.Usage.Builder.class)
    public static class Usage {
        private final Integer completionTokens;
        private final CompletionTokensDetails completionTokensDetails;
        private final Integer promptCacheHitTokens;
        private final Integer promptCacheMissTokens;
        private final Integer promptTokens;
        private final PromptTokensDetails promptTokensDetails;
        private final Integer totalTokens;

        public Usage(Builder builder) {
            this.completionTokens = builder.completionTokens;
            this.completionTokensDetails = builder.completionTokensDetails;
            this.promptCacheHitTokens = builder.promptCacheHitTokens;
            this.promptCacheMissTokens = builder.promptCacheMissTokens;
            this.promptTokens = builder.promptTokens;
            this.promptTokensDetails = builder.promptTokensDetails;
            this.totalTokens = builder.totalTokens;
        }

        public Integer completionTokens() {
            return this.completionTokens;
        }

        public CompletionTokensDetails completionTokensDetails() {
            return this.completionTokensDetails;
        }

        public Integer promptCacheHitTokens() {
            return this.promptCacheHitTokens;
        }

        public Integer promptCacheMissTokens() {
            return this.promptCacheMissTokens;
        }

        public Integer promptTokens() {
            return this.promptTokens;
        }

        public PromptTokensDetails promptTokensDetails() {
            return this.promptTokensDetails;
        }

        public Integer totalTokens() {
            return this.totalTokens;
        }

        @Override
        public String toString() {
            return "Usage{" +
                    "completionTokens=" + completionTokens +
                    ", completionTokensDetails=" + completionTokensDetails +
                    ", promptCacheHitTokens=" + promptCacheHitTokens +
                    ", promptCacheMissTokens=" + promptCacheMissTokens +
                    ", promptTokens=" + promptTokens +
                    ", promptTokensDetails=" + promptTokensDetails +
                    ", totalTokens=" + totalTokens +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            @JsonProperty("completion_tokens") private Integer completionTokens;
            @JsonProperty("completion_tokens_details") private CompletionTokensDetails completionTokensDetails;
            @JsonProperty("prompt_cache_hit_tokens") private Integer promptCacheHitTokens;
            @JsonProperty("prompt_cache_miss_tokens") private Integer promptCacheMissTokens;
            @JsonProperty("prompt_tokens") private Integer promptTokens;
            @JsonProperty("prompt_tokens_details") private PromptTokensDetails promptTokensDetails;
            @JsonProperty("total_tokens") private Integer totalTokens;

            public Builder completionTokens(Integer completionTokens) {
                this.completionTokens = completionTokens;
                return this;
            }

            public Builder completionTokensDetails(CompletionTokensDetails completionTokensDetails) {
                this.completionTokensDetails = completionTokensDetails;
                return this;
            }

            public Builder promptCacheHitTokens(Integer promptCacheHitTokens) {
                this.promptCacheHitTokens = promptCacheHitTokens;
                return this;
            }

            public Builder promptCacheMissTokens(Integer promptCacheMissTokens) {
                this.promptCacheMissTokens = promptCacheMissTokens;
                return this;
            }

            public Builder promptTokens(Integer promptTokens) {
                this.promptTokens = promptTokens;
                return this;
            }

            public Builder promptTokensDetails(PromptTokensDetails promptTokensDetails) {
                this.promptTokensDetails = promptTokensDetails;
                return this;
            }

            public Builder totalTokens(Integer totalTokens) {
                this.totalTokens = totalTokens;
                return this;
            }

            public Usage build() {
                return new Usage(this);
            }
        }

        @JsonDeserialize(builder = ChatCompletionResponseParam.Usage.CompletionTokensDetails.Builder.class)
        public static class CompletionTokensDetails {
            private final Integer reasoningTokens;

            public CompletionTokensDetails(Builder builder) {
                this.reasoningTokens = builder.reasoningTokens;
            }

            public Integer reasoningTokens() {
                return this.reasoningTokens;
            }

            @Override
            public String toString() {
                return "CompletionTokensDetails{" +
                        "reasoningTokens=" + reasoningTokens +
                        '}';
            }

            public static Builder builder() {
                return new Builder();
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            @JsonPOJOBuilder(withPrefix = "")
            public static class Builder {
                @JsonProperty("reasoning_tokens") private Integer reasoningTokens;

                public Builder reasoningTokens(Integer reasoningTokens) {
                    this.reasoningTokens = reasoningTokens;
                    return this;
                }

                public CompletionTokensDetails build() {
                    return new CompletionTokensDetails(this);
                }
            }

        }

        @JsonDeserialize(builder = ChatCompletionResponseParam.Usage.PromptTokensDetails.Builder.class)
        public static class PromptTokensDetails {
            private final Integer cachedTokens;

            public PromptTokensDetails(Builder builder) {
                this.cachedTokens = builder.cachedTokens;
            }

            public Integer cachedTokens() {
                return this.cachedTokens;
            }

            @Override
            public String toString() {
                return "PromptTokensDetails{" +
                        "cachedTokens=" + cachedTokens +
                        '}';
            }

            public static Builder builder() {
                return new Builder();
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            @JsonPOJOBuilder(withPrefix = "")
            public static final class Builder {
                @JsonProperty("cached_tokens") private Integer cachedTokens;

                public Builder cachedTokens(Integer cachedTokens) {
                    this.cachedTokens = cachedTokens;
                    return this;
                }

                public PromptTokensDetails build() {
                    return new PromptTokensDetails(this);
                }
            }
        }
    }
}
