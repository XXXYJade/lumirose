package io.github.xxxyjade.lumirose.agent;

import cn.hutool.core.util.IdUtil;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.*;
import io.github.xxxyjade.lumirose.repository.MessageRepository;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.ToolRegistry;
import io.github.xxxyjade.lumirose.toolkit.MessageFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class LumiRoseAgent {

    private final OpenAIClient client;
    private final String model;
    private final String systemMessage;
    private final ToolRegistry toolRegistry;
    private final MessageRepository repository;
    private final Integer maxTurns;

    public String run(String content) {
        return run(IdUtil.getSnowflakeNextId(), content);
    }

    public String run(Long conversationId, String content) {
        List<ChatCompletionMessageParam> appendMessages = new ArrayList<>();
        List<ChatCompletionMessageParam> messageMemoryParams = repository.load(conversationId);
        ChatCompletionCreateParams.Builder paramBuilder = ChatCompletionCreateParams.builder()
                .model(model);

        if (messageMemoryParams.isEmpty() && systemMessage != null && !systemMessage.isBlank()) {
            ChatCompletionMessageParam systemMessageParam = MessageFactory.system(systemMessage);
            appendMessages.add(systemMessageParam);
            paramBuilder.addMessage(systemMessageParam);
        }

        messageMemoryParams.forEach(paramBuilder::addMessage);
        ChatCompletionMessageParam userMessageParam = MessageFactory.user(content);
        paramBuilder.addMessage(userMessageParam);
        appendMessages.add(userMessageParam);

        toolRegistry.getTools().forEach(paramBuilder::addTool);

        int current = 0;
        while (current++ < maxTurns) {
            boolean hasToolCall = false;
            ChatCompletion chatCompletion = client.chat().completions().create(paramBuilder.build());

            for (ChatCompletion.Choice choice : chatCompletion.choices()) {
                ChatCompletionMessageParam messageParam = MessageFactory.assistant(choice.message());
                paramBuilder.addMessage(messageParam);
                appendMessages.add(messageParam);

                List<ChatCompletionMessageToolCall> toolCalls = messageParam.asAssistant().toolCalls().orElse(List.of());

                if (!hasToolCall && !toolCalls.isEmpty()) {
                    hasToolCall = true;
                }

                for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                    ChatCompletionMessageParam toolCallMessageParam = MessageFactory.tool(toolCall.asFunction().id(), toolExecute(toolCall));
                    appendMessages.add(toolCallMessageParam);
                    paramBuilder.addMessage(toolCallMessageParam);
                }
            }

            if (!hasToolCall) {
                repository.append(conversationId, appendMessages);
                return chatCompletion.choices().get(0).message().content().orElse("服务器异常");
            }
        }

        return "请求超时";
    }


    public Flux<String> stream(String content) {
        return stream(IdUtil.getSnowflakeNextId(), content);
    }

    public Flux<String> stream(Long conversationId, String content) {
            return Flux.<String>create(sink -> {
                // 创建新增消息列表：用于存储用户的输入和大模型输出
                List<ChatCompletionMessageParam> appendMessages = new ArrayList<>();

                // 加载历史消息
                List<ChatCompletionMessageParam> memoryMessageParams = repository.load(conversationId);

                // ParamBuilder：用于构造请求参数
                ChatCompletionCreateParams.Builder paramBuilder = ChatCompletionCreateParams.builder()
                        .model(model);

                // 若历史消息为空且系统消息不为空，则添加系统消息到请求参数和新增消息列表
                if (memoryMessageParams.isEmpty() && systemMessage != null && !systemMessage.isBlank()) {
                    ChatCompletionMessageParam systemMessageParam = MessageFactory.system(systemMessage);
                    paramBuilder.addMessage(systemMessageParam);
                    appendMessages.add(systemMessageParam);
                }

                // 添加历史消息到请求参数
                memoryMessageParams.forEach(paramBuilder::addMessage);

                // 根据用户输入构建用户消息，并添加到请求参数和新增消息列表
                ChatCompletionMessageParam userMessageParam = MessageFactory.user(content);
                paramBuilder.addMessage(userMessageParam);
                appendMessages.add(userMessageParam);

                // 添加工具信息到请求参数
                toolRegistry.getTools().forEach(paramBuilder::addTool);

                // current：当前轮数，若大于最大轮数，自动结束循环
                int current = 0;
                while (current++ < maxTurns) {
                    // ToolCall标识
                    boolean hasToolCall = false;

                    // 创建accumulator：整合流式 ChatCompletionChunk，累积成一个完整的 ChatCompletion 对象
                    ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();

                    try (StreamResponse<ChatCompletionChunk> response = client.chat().completions().createStreaming(paramBuilder.build())) {
                        response.stream()
                                .peek(accumulator::accumulate)
                                .flatMap(chunk -> chunk.choices().stream())
                                .flatMap(choice -> choice.delta().content().stream())
                                .forEach(sink::next);
                    }
                    ChatCompletion chatCompletion = accumulator.chatCompletion();

                    // 遍历响应结果
                    for (ChatCompletion.Choice choice : chatCompletion.choices()) {
                        // 将choice.message()转化为ChatCompletionMessageParam用于持久化，并添加到请求参数和新增消息列表
                        ChatCompletionMessageParam messageParam = MessageFactory.assistant(choice.message());
                        paramBuilder.addMessage(messageParam);
                        appendMessages.add(messageParam);

                        // 遍历toolCalls，并修改toolCall标记
                        List<ChatCompletionMessageToolCall> toolCalls = messageParam.asAssistant().toolCalls().orElse(List.of());

                        // 若hasToolCall = false 且当前choice有toolCalls，则修改hasToolCall标识
                        if (!hasToolCall && !toolCalls.isEmpty()) {
                            hasToolCall = true;
                        }

                        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                            // 将ChatCompletionMessageToolCall转化为ChatCompletionMessageParam，并添加到请求参数和新增消息列表
                            ChatCompletionMessageParam toolCallMessageParam = MessageFactory.tool(toolCall.asFunction().id(), toolExecute(toolCall));
                            appendMessages.add(toolCallMessageParam);
                            paramBuilder.addMessage(toolCallMessageParam);
                        }
                    }

                    if (!hasToolCall) {
                        break;
                    }
                }

                // repository追加新增消息，并结束sink
                repository.append(conversationId, appendMessages);
                sink.complete();
            }).subscribeOn(Schedulers.boundedElastic()) ;
    }

    private Object toolExecute(ChatCompletionMessageToolCall toolCall) {
        ChatCompletionMessageFunctionToolCall functionToolCall = toolCall.asFunction();
        Class<? extends Tool> clazz = toolRegistry.getTool(functionToolCall.function().name());
        return functionToolCall.function()
                .arguments(clazz)
                .execute();
    }

    private LumiRoseAgent(Builder builder) {
        this.client = builder.client;
        this.model = builder.model;
        this.systemMessage = builder.systemMessage;
        this.toolRegistry = builder.toolRegistry;
        this.repository = builder.repository;
        this.maxTurns = builder.maxTurns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private OpenAIClient client;
        private String model;
        private String systemMessage;
        private ToolRegistry toolRegistry;
        private MessageRepository repository;
        private Integer maxTurns = 100;

        public Builder client(OpenAIClient client)          {
            this.client = client;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder repository(MessageRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder maxTurns(Integer maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public LumiRoseAgent build() throws Exception {
            if (client == null) {
                throw new IllegalStateException("client is required");
            }

            if (model == null) {
                throw new IllegalStateException("model is required");
            }

            if (toolRegistry == null) {
                throw new IllegalStateException("toolRegistry is required");
            }

            if (repository == null) {
                throw new IllegalStateException("repository is required");
            }
            return new LumiRoseAgent(this);
        }
    }

}
