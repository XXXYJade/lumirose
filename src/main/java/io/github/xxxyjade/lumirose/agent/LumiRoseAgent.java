package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.ConfigurationException;
import io.github.xxxyjade.lumirose.exception.LumiRoseErrorCode;
import io.github.xxxyjade.lumirose.exception.LumiRoseException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单 Agent 同步运行：加载历史、请求模型、执行工具、原子提交。
 */
public final class LumiRoseAgent {

    private static final String DEFAULT_SYSTEM_MESSAGE = "You are a helpful assistant!";
    private static final Logger log = LoggerFactory.getLogger(LumiRoseAgent.class);

    private final LumiRoseClient client;
    private final ToolContext toolContext;
    private final MessageRepository repository;
    private final String systemMessage;
    private final int maxTurns;
    private final MetricsRecorder metricsRecorder;

    /**
     * 运行一次对话，返回最终文本。conversationId 为 null 时生成一个正数 id，且不返回。
     */
    public String run(Long conversationId, String content) {
        return runResult(conversationId, content).content();
    }

    /**
     * 运行一次对话，返回包含文本、轮次与用量的结果。
     */
    public RunResult runResult(Long conversationId, String content) {
        return execute(conversationId, content);
    }

    private RunResult execute(Long conversationId, String content) {
        if (content == null || content.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "content 不能为空");
        }
        Long id = conversationId != null
                ? conversationId
                : ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        long runStartedAt = System.nanoTime();
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

        log.debug("run start: conversationId={} historySize={} toolCount={} maxTurns={}",
                id, history.size(), tools.size(), maxTurns);

        List<ToolCallRecord> toolCallRecords = new ArrayList<>();
        int promptTokens = 0;
        int completionTokens = 0;

        try {
            int turn = 0;
            while (turn < maxTurns) {
                turn++;

                log.debug("turn {} request: messages={} tools={}", turn, messages.size(), tools.size());
                long requestStartedAt = System.nanoTime();
                ChatCompletionResponseParam response;
                try {
                    response = client.chatCompletion(
                            ChatCompletionRequestParam.builder()
                                    .messages(messages)
                                    .tools(tools)
                                    .build());
                } catch (RuntimeException e) {
                    recordRequest(turn, elapsedMillis(requestStartedAt), false);
                    throw e;
                }
                long requestMillis = elapsedMillis(requestStartedAt);
                recordRequest(turn, requestMillis, true);

                ChatCompletionResponseParam.Choice choice = response.choices().get(0);
                ChatCompletionMessage message = choice.message();
                FinishReason finishReason = FinishReason.from(choice.finishReason());
                boolean hasToolCalls = message.toolCalls() != null && !message.toolCalls().isEmpty();
                ChatCompletionResponseParam.Usage usage = response.usage();

                if (usage != null) {
                    promptTokens += nullSafe(usage.promptTokens());
                    completionTokens += nullSafe(usage.completionTokens());
                    recordUsage(usage.promptTokens(), usage.completionTokens());
                }

                log.debug("turn {} response: finishReason={} hasToolCalls={} elapsedMs={} promptTokens={} completionTokens={}",
                        turn, choice.finishReason(), hasToolCalls, requestMillis,
                        usage == null ? null : usage.promptTokens(),
                        usage == null ? null : usage.completionTokens());

                if (finishReason == FinishReason.LENGTH) {
                    throw new AgentException(LumiRoseErrorCode.AGENT_OUTPUT_TRUNCATED, "模型输出被截断");
                }

                if (hasToolCalls) {
                    executeToolCalls(message, messages, pending, turn, toolCallRecords);
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
                long elapsed = elapsedMillis(runStartedAt);
                log.debug("run end: turns={} committed={} elapsedMs={}", turn, true, elapsed);
                return new RunResult(message.content(), turn, elapsed,
                        promptTokens, completionTokens, toolCallRecords);
            }

            throw new AgentException(LumiRoseErrorCode.AGENT_TURN_LIMIT, "轮次用尽");
        } catch (LumiRoseException e) {
            log.error("run error: code={} type={}", e.getErrorCode().code(), e.getClass().getSimpleName());
            throw e;
        }
    }

    private void executeToolCalls(ChatCompletionMessage message,
                                  List<ChatCompletionMessageParam> messages,
                                  List<ChatCompletionMessageParam> pending,
                                  int turn,
                                  List<ToolCallRecord> toolCallRecords) {
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

            String name = toolCall.function().name();
            long toolStartedAt = System.nanoTime();
            String result;
            try {
                result = toolContext.execute(name, toolCall.function().arguments());
            } catch (RuntimeException e) {
                recordTool(name, elapsedMillis(toolStartedAt), false);
                log.debug("turn {} tool {} failed elapsedMs={}", turn, name, elapsedMillis(toolStartedAt));
                throw e;
            }
            long toolMillis = elapsedMillis(toolStartedAt);
            recordTool(name, toolMillis, true);
            toolCallRecords.add(new ToolCallRecord(toolCall.id(), name, toolMillis));
            log.debug("turn {} tool {} ok elapsedMs={}", turn, name, toolMillis);

            ChatCompletionToolMessageParam toolMessage = ChatCompletionToolMessageParam.builder()
                    .toolCallId(toolCall.id())
                    .content(result == null ? "" : result)
                    .build();
            messages.add(toolMessage);
            pending.add(toolMessage);
        }
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static ChatCompletionAssistantMessageParam assistantOf(ChatCompletionMessage message) {
        return ChatCompletionAssistantMessageParam.builder()
                .content(message.content())
                .reasoningContent(message.reasoningContent())
                .toolCalls(message.toolCalls())
                .build();
    }

    private void recordRequest(int turn, long millis, boolean success) {
        try {
            metricsRecorder.onRequest(turn, millis, success);
        } catch (RuntimeException e) {
            log.debug("metrics failed: {}", e.getClass().getSimpleName());
        }
    }

    private void recordUsage(Integer promptTokens, Integer completionTokens) {
        try {
            metricsRecorder.onUsage(nullSafe(promptTokens), nullSafe(completionTokens));
        } catch (RuntimeException e) {
            log.debug("metrics failed: {}", e.getClass().getSimpleName());
        }
    }

    private void recordTool(String toolName, long millis, boolean success) {
        try {
            metricsRecorder.onTool(toolName, millis, success);
        } catch (RuntimeException e) {
            log.debug("metrics failed: {}", e.getClass().getSimpleName());
        }
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
        this.metricsRecorder = builder.metricsRecorder;
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
        private MetricsRecorder metricsRecorder = MetricsRecorder.NOOP;

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

        public Builder metricsRecorder(MetricsRecorder metricsRecorder) {
            this.metricsRecorder = metricsRecorder;
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
            if (metricsRecorder == null) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "metricsRecorder 不能为空");
            }
            if (maxTurns < 1) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "maxTurns 必须大于 0");
            }
            return new LumiRoseAgent(this);
        }
    }
}
