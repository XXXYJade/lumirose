package io.github.xxxyjade.lumirose.agent;

//import com.openai.models.chat.completions.*;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.model.chat.completion.*;
import io.github.xxxyjade.lumirose.repository.MessageRepository;
import io.github.xxxyjade.lumirose.tool.Tool;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;

import java.util.ArrayList;
import java.util.List;

public class LumiRoseAgent {

    private static final ChatCompletionSystemMessageParam systemMessage = ChatCompletionSystemMessageParam.builder()
            .content("You are a helpful assistant!")
            .build();

    private final LumiRoseClient client;
    private final ToolContext toolContext;
    private final MessageRepository repository;
    private final Integer maxTurns;

    public String run(Long conversationId, String content) {
        // 创建消息参数列表
        List<ChatCompletionMessageParam> messageParams = new ArrayList<>();

        // 若系统消息不为空，则加入历史消息列表
        if (systemMessage != null) {
            messageParams.add(systemMessage);
        }

        // 从repository加载历史消息，并加入消息参数列表
        List<ChatCompletionMessageParam> messageMemory = repository.load(conversationId);
        messageParams.addAll(messageMemory);

        // 构建用户消息，并加入消息列表
        ChatCompletionUserMessageParam userMessageParam = ChatCompletionUserMessageParam.builder()
                .content(content)
                .build();
        messageParams.add(userMessageParam);

        // 从toolContext加载工具列表
        List<Tool> tools = toolContext.tools();
        List<ChatCompletionToolParam> toolParam = tools.stream()
                .map(tool -> ChatCompletionToolParam.builder()
                        .function(tool)
                        .build())
                .toList();

        // 定义turn计数器、toolCall标记、响应结果
        int iterator = 0;
        boolean hasToolCall = true;
        ChatCompletionAssistantMessageParam result = null;

        while (iterator++ < maxTurns && hasToolCall) {
            // 构建ChatCompletion请求参数，并发起请求
            ChatCompletionRequestParam requestParam = ChatCompletionRequestParam.builder()
                    .messages(messageParams)
                    .tools(toolParam)
                    .build();
            ChatCompletionResponseParam responseParam = client.chatCompletion(requestParam);

            hasToolCall = false;
            // 遍历解析响应参数
            for (ChatCompletionResponseParam.Choice choice : responseParam.choices()) {
                ChatCompletionMessage message = choice.message();
                ChatCompletionAssistantMessageParam assistantMessageParam = ChatCompletionAssistantMessageParam.builder()
                        .content(message.content())
                        .reasoningContent(message.reasoningContent())
                        .toolCalls(message.toolCalls())
                        .build();
                result = assistantMessageParam;
                repository.appendAll(conversationId, List.of(assistantMessageParam));
                messageParams.add(assistantMessageParam);

                if (hasToolCall || choice.finishReason().equals("tool_calls")) {
                    hasToolCall = true;

                    for (ChatCompletionToolCall toolCall : message.toolCalls()) {
                        ChatCompletionToolCall.Function function = toolCall.function();
                        String toolKey = function.name();
                        String res = toolContext.execute(toolKey, function.arguments());
                        ChatCompletionToolMessageParam toolMessageParam = ChatCompletionToolMessageParam.builder()
                                .toolCallId(toolCall.id())
                                .content(res)
                                .build();
                        repository.appendAll(conversationId, List.of(toolMessageParam));
                        messageParams.add(toolMessageParam);
                    }
                }
            }
        }

        return result.content();
    }

//    public String run(String content) {
//        return run(IdUtil.getSnowflakeNextId(), content);
//    }

//    public String run(Long conversationId, String content) {
//        List<ChatCompletionMessageParam> appendMessages = new ArrayList<>();
//        List<ChatCompletionMessageParam> messageMemoryParams = repository.load(conversationId);
//        ChatCompletionCreateParams.Builder paramBuilder = ChatCompletionCreateParams.builder()
//                .model(model);
//
//        if (messageMemoryParams.isEmpty() && systemMessage != null && !systemMessage.isBlank()) {
//            ChatCompletionMessageParam systemMessageParam = MessageFactory.system(systemMessage);
//            appendMessages.add(systemMessageParam);
//            paramBuilder.addMessage(systemMessageParam);
//        }
//
//        messageMemoryParams.forEach(paramBuilder::addMessage);
//        ChatCompletionMessageParam userMessageParam = MessageFactory.user(content);
//        paramBuilder.addMessage(userMessageParam);
//        appendMessages.add(userMessageParam);
//
////        paramBuilder.addT
////        toolContext.getTools().forEach(paramBuilder::addTool);
//
//        int current = 0;
//        while (current++ < maxTurns) {
//            boolean hasToolCall = false;
//            ChatCompletion chatCompletion = client.chat().completions().create(paramBuilder.build());
//
//            for (ChatCompletion.Choice choice : chatCompletion.choices()) {
//                ChatCompletionMessageParam messageParam = MessageFactory.assistant(choice.message());
//                paramBuilder.addMessage(messageParam);
//                appendMessages.add(messageParam);
//
//                List<ChatCompletionMessageToolCall> toolCalls = messageParam.asAssistant().toolCalls().orElse(List.of());
//
//                if (!hasToolCall && !toolCalls.isEmpty()) {
//                    hasToolCall = true;
//                }
//
//                for (ChatCompletionMessageToolCall toolCall : toolCalls) {
//                    ChatCompletionMessageParam toolCallMessageParam = MessageFactory.tool(toolCall.asFunction().id(), toolExecute(toolCall));
//                    appendMessages.add(toolCallMessageParam);
//                    paramBuilder.addMessage(toolCallMessageParam);
//                }
//            }
//
//            if (!hasToolCall) {
//                repository.append(conversationId, appendMessages);
//                return chatCompletion.choices().get(0).message().content().orElse("服务器异常");
//            }
//        }
//
//        return "请求超时";
//    }


//    public Flux<String> stream(String content) {
//        return stream(IdUtil.getSnowflakeNextId(), content);
//    }
//
//    public Flux<String> stream(Long conversationId, String content) {
//            return Flux.<String>create(sink -> {
//                // 创建新增消息列表：用于存储用户的输入和大模型输出
//                List<ChatCompletionMessageParam> appendMessages = new ArrayList<>();
//
//                // 加载历史消息
//                List<ChatCompletionMessageParam> memoryMessageParams = repository.load(conversationId);
//
//                // ParamBuilder：用于构造请求参数
////                ChatCompletionCreateParams.Builder paramBuilder = ChatCompletionCreateParams.builder()
////                        .model(model);
//
//                // 若历史消息为空且系统消息不为空，则添加系统消息到请求参数和新增消息列表
//                if (memoryMessageParams.isEmpty() && systemMessage != null && !systemMessage.isBlank()) {
//                    ChatCompletionMessageParam systemMessageParam = MessageFactory.system(systemMessage);
//                    paramBuilder.addMessage(systemMessageParam);
//                    appendMessages.add(systemMessageParam);
//                }
//
//                // 添加历史消息到请求参数
//                memoryMessageParams.forEach(paramBuilder::addMessage);
//
//                // 根据用户输入构建用户消息，并添加到请求参数和新增消息列表
//                ChatCompletionMessageParam userMessageParam = MessageFactory.user(content);
//                paramBuilder.addMessage(userMessageParam);
//                appendMessages.add(userMessageParam);
//
//
////                paramBuilder.addMess
////
////                // 添加工具信息到请求参数
////                toolContext.getTools().forEach(paramBuilder::addTool);
////                ChatCompletionFunctionTool.builder()
//
//                // current：当前轮数，若大于最大轮数，自动结束循环
//                int current = 0;
//                while (current++ < maxTurns) {
//                    // ToolCall标识
//                    boolean hasToolCall = false;
//
//                    // 创建accumulator：整合流式 ChatCompletionChunk，累积成一个完整的 ChatCompletion 对象
//                    ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
//
//                    try (StreamResponse<ChatCompletionChunk> response = client.chat().completions().createStreaming(paramBuilder.build())) {
//                        response.stream()
//                                .peek(accumulator::accumulate)
//                                .flatMap(chunk -> chunk.choices().stream())
//                                .flatMap(choice -> choice.delta().content().stream())
//                                .forEach(sink::next);
//                    }
//                    ChatCompletion chatCompletion = accumulator.chatCompletion();
//
//                    // 遍历响应结果
//                    for (ChatCompletion.Choice choice : chatCompletion.choices()) {
//                        // 将choice.message()转化为ChatCompletionMessageParam用于持久化，并添加到请求参数和新增消息列表
//                        ChatCompletionMessageParam messageParam = MessageFactory.assistant(choice.message());
//                        paramBuilder.addMessage(messageParam);
//                        appendMessages.add(messageParam);
//
//                        // 遍历toolCalls，并修改toolCall标记
//                        List<ChatCompletionMessageToolCall> toolCalls = messageParam.asAssistant().toolCalls().orElse(List.of());
//
//                        // 若hasToolCall = false 且当前choice有toolCalls，则修改hasToolCall标识
//                        if (!hasToolCall && !toolCalls.isEmpty()) {
//                            hasToolCall = true;
//                        }
//
//                        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
//                            // 将ChatCompletionMessageToolCall转化为ChatCompletionMessageParam，并添加到请求参数和新增消息列表
//                            ChatCompletionMessageParam toolCallMessageParam = MessageFactory.tool(toolCall.asFunction().id(), toolExecute(toolCall));
//                            appendMessages.add(toolCallMessageParam);
//                            paramBuilder.addMessage(toolCallMessageParam);
//                        }
//                    }
//
//                    if (!hasToolCall) {
//                        break;
//                    }
//                }
//
//                // repository追加新增消息，并结束sink
//                repository.append(conversationId, appendMessages);
//                sink.complete();
//            }).subscribeOn(Schedulers.boundedElastic()) ;
//    }

//    private Object toolExecute(ChatCompletionMessageToolCall toolCall) {
//        ChatCompletionMessageFunctionToolCall functionToolCall = toolCall.asFunction();
//        return null;
////        Class<? extends Tool> clazz = toolContext.getTool(functionToolCall.function().name());
////        return functionToolCall.function()
////                .arguments(clazz)
////                .execute();
//    }

    private LumiRoseAgent(Builder builder) {
        this.client = builder.client;
//        this.model = builder.model;
//        this.systemMessage = builder.systemMessage;
        this.toolContext = builder.toolContext;
        this.repository = builder.repository;
        this.maxTurns = builder.maxTurns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private LumiRoseClient client;
        private String model;
//        private String systemMessage;
        private ToolContext toolContext;
        private MessageRepository repository;
        private Integer maxTurns = 100;

        public Builder client(LumiRoseClient client)          {
            this.client = client;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

//        public Builder systemMessage(String systemMessage) {
//            this.systemMessage = systemMessage;
//            return this;
//        }

        public Builder toolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
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

        public LumiRoseAgent build() {
            if (client == null) {
                throw new IllegalStateException("client is required");
            }

            if (model == null) {
                throw new IllegalStateException("model is required");
            }

            if (toolContext == null) {
                throw new IllegalStateException("toolRegistry is required");
            }

            if (repository == null) {
                throw new IllegalStateException("repository is required");
            }
            return new LumiRoseAgent(this);
        }
    }

}
