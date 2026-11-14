package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.StorageException;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionAssistantMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessage;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionRequestParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionResponseParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionSystemMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.FinishReason;
import io.github.xxxyjade.lumirose.repository.MessageRepository;
import io.github.xxxyjade.lumirose.tool.context.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单 Agent 同步运行：加载历史、请求模型、执行工具、原子提交。
 */
public final class LumiRoseAgent {

    private static final String DEFAULT_SYSTEM_MESSAGE = "You are a helpful assistant!";

    private final LumiRoseClient client;
    private final ToolContext toolContext;
    private final MessageRepository repository;
    private final String systemMessage;
    private final int maxTurns;

    /**
     * 运行一次对话。conversationId 为 null 时生成一个正数 id，且不返回。
     */
    public String run(Long conversationId, String content) {
        if (content == null || content.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "content 不能为空");
        }
        Long id = conversationId != null
                ? conversationId
                : ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        List<ChatCompletionMessageParam> history = load(id);
        List<ChatCompletionMessageParam> pending = new ArrayList<>();
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        if (history.isEmpty() && systemMessage != null && !systemMessage.isBlank()) {
            ChatCompletionSystemMessageParam system = ChatCompletionSystemMessageParam.builder()
                    .content(systemMessage)
                    .build();
            messages.add(system);
            pending.add(system);
        }
        messages.addAll(history);

        ChatCompletionUserMessageParam user = ChatCompletionUserMessageParam.builder()
                .content(content)
                .build();
        messages.add(user);
        pending.add(user);

        List<ChatCompletionToolParam> tools = toolContext.tools().stream()
                .map(tool -> ChatCompletionToolParam.builder().function(tool).build())
                .toList();

        int turn = 0;
        while (turn < maxTurns) {
            turn++;

            ChatCompletionResponseParam response = client.chatCompletion(
                    ChatCompletionRequestParam.builder()
                            .messages(messages)
                            .tools(tools)
                            .build());

            ChatCompletionResponseParam.Choice choice = response.choices().get(0);
            ChatCompletionMessage message = choice.message();
            FinishReason finishReason = FinishReason.from(choice.finishReason());

            if (finishReason == FinishReason.LENGTH) {
                throw new AgentException(LumiRoseErrorCode.AGENT_OUTPUT_TRUNCATED, "模型输出被截断");
            }

            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                executeToolCalls(message, messages, pending);
                continue;
            }

            if (finishReason != FinishReason.STOP) {
                throw new AgentException(LumiRoseErrorCode.AGENT_RESPONSE_INVALID, "模型响应语义非法");
            }
            if (message.content() == null || message.content().isBlank()) {
                throw new AgentException(LumiRoseErrorCode.AGENT_RESPONSE_INVALID, "模型响应语义非法");
            }

            ChatCompletionAssistantMessageParam assistant = assistantOf(message);
            messages.add(assistant);
            pending.add(assistant);

            appendAll(id, pending);
            return message.content();
        }

        throw new AgentException(LumiRoseErrorCode.AGENT_TURN_LIMIT, "轮次用尽");
    }

    private void executeToolCalls(ChatCompletionMessage message,
                                  List<ChatCompletionMessageParam> messages,
                                  List<ChatCompletionMessageParam> pending) {
        ChatCompletionAssistantMessageParam assistant = assistantOf(message);
        messages.add(assistant);
        pending.add(assistant);

        for (ChatCompletionToolCall toolCall : message.toolCalls()) {
            if (toolCall.function() == null
                    || toolCall.function().name() == null
                    || toolCall.function().name().isBlank()
                    || toolCall.id() == null
                    || toolCall.id().isBlank()) {
                throw new AgentException(LumiRoseErrorCode.AGENT_RESPONSE_INVALID, "工具调用结构非法");
            }

            String result = toolContext.execute(toolCall.function().name(), toolCall.function().arguments());
            ChatCompletionToolMessageParam toolMessage = ChatCompletionToolMessageParam.builder()
                    .toolCallId(toolCall.id())
                    .content(result == null ? "" : result)
                    .build();
            messages.add(toolMessage);
            pending.add(toolMessage);
        }
    }

    private static ChatCompletionAssistantMessageParam assistantOf(ChatCompletionMessage message) {
        return ChatCompletionAssistantMessageParam.builder()
                .content(message.content())
                .reasoningContent(message.reasoningContent())
                .toolCalls(message.toolCalls())
                .build();
    }

    private List<ChatCompletionMessageParam> load(Long conversationId) {
        try {
            return repository.load(conversationId);
        } catch (RuntimeException e) {
            throw new StorageException(LumiRoseErrorCode.STORAGE_FAILED, "仓储读取失败", e);
        }
    }

    private void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages) {
        try {
            repository.appendAll(conversationId, messages);
        } catch (RuntimeException e) {
            throw new StorageException(LumiRoseErrorCode.STORAGE_FAILED, "仓储写入失败", e);
        }
    }

    private LumiRoseAgent(Builder builder) {
        this.client = builder.client;
        this.toolContext = builder.toolContext;
        this.repository = builder.repository;
        this.systemMessage = builder.systemMessage;
        this.maxTurns = builder.maxTurns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private LumiRoseClient client;
        private ToolContext toolContext;
        private MessageRepository repository;
        private String systemMessage = DEFAULT_SYSTEM_MESSAGE;
        private int maxTurns = 10;

        public Builder client(LumiRoseClient client) {
            this.client = client;
            return this;
        }

        public Builder toolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
            return this;
        }

        public Builder repository(MessageRepository repository) {
            this.repository = repository;
            return this;
        }

        /**
         * null 或空白表示不发送 system 消息。
         */
        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public LumiRoseAgent build() {
            if (client == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "client 不能为空");
            }
            if (toolContext == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "toolContext 不能为空");
            }
            if (repository == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "repository 不能为空");
            }
            return new LumiRoseAgent(this);
        }
    }
}
