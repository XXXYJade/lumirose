package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.client.CompletionStreamListener;
import io.github.xxxyjade.lumirose.client.LumiRoseClient;
import io.github.xxxyjade.lumirose.exception.AgentException;
import io.github.xxxyjade.lumirose.exception.ClientException;
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
import java.util.concurrent.locks.ReentrantLock;

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
    private final int maxRetries;
    private final long retryBaseDelayMillis;
    private final long retryMaxDelayMillis;
    private final Integer contextBudgetChars;

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
        return execute(conversationId, content, null, false);
    }

    /**
     * 以流式方式运行一次对话，边接收边回调事件。
     */
    public RunResult runStream(Long conversationId, String content, StreamListener listener) {
        if (listener == null) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "listener 不能为空");
        }
        return execute(conversationId, content, listener, true);
    }

    private RunResult execute(Long conversationId, String content, StreamListener listener, boolean streaming) {
        if (content == null || content.isBlank()) {
            throw new AgentException(LumiRoseErrorCode.PARAM_INVALID, "content 不能为空");
        }
        Long id = conversationId != null
                ? conversationId
                : ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        ReentrantLock lock = ConversationLocks.forId(id);
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentException(LumiRoseErrorCode.AGENT_CANCELLED, "等待会话锁被中断");
        }
        try {
            return runLocked(id, content, listener, streaming);
        } finally {
            lock.unlock();
        }
    }

    private RunResult runLocked(Long id, String content, StreamListener listener, boolean streaming) {
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

        Control control = streaming ? new Control() : null;
        if (streaming) {
            listener.onStart(control);
        }

        List<ToolCallRecord> toolCallRecords = new ArrayList<>();
        int promptTokens = 0;
        int completionTokens = 0;
        int turn = 0;

        try {
            while (turn < maxTurns) {
                turn++;
                checkCancelled(control);

                List<ChatCompletionMessageParam> requestMessages = messages;
                if (contextBudgetChars != null) {
                    requestMessages = ContextTrimmer.trim(messages, contextBudgetChars);
                    int estimated = ContextTrimmer.estimate(messages);
                    if (estimated > contextBudgetChars) {
                        log.debug("trim: turn={} estimatedChars={} budgetChars={} droppedMessages={}",
                                turn, estimated, contextBudgetChars, messages.size() - requestMessages.size());
                    }
                }

                log.debug("turn {} request: messages={} tools={}", turn, requestMessages.size(), tools.size());
                ChatCompletionRequestParam request = ChatCompletionRequestParam.builder()
                        .messages(requestMessages)
                        .tools(tools)
                        .build();
                boolean[] delivered = {false};
                long requestStartedAt = System.nanoTime();
                ChatCompletionResponseParam response = requestWithRetry(
                        request, turn, listener, control, streaming, delivered);
                long requestMillis = elapsedMillis(requestStartedAt);

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
                    executeToolCalls(message, messages, pending, turn, toolCallRecords, listener, control, streaming);
                    fireTurnEnd(listener, streaming, turn, finishReason, usage);
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

                fireTurnEnd(listener, streaming, turn, finishReason, usage);
                checkCancelled(control);

                appendAll(id, pending);
                long elapsed = elapsedMillis(runStartedAt);
                log.debug("run end: turns={} committed={} elapsedMs={}", turn, true, elapsed);
                return new RunResult(message.content(), turn, elapsed,
                        promptTokens, completionTokens, toolCallRecords);
            }

            throw new AgentException(LumiRoseErrorCode.AGENT_TURN_LIMIT, "轮次用尽");
        } catch (CancelledRun e) {
            log.debug("cancel: turn={}", turn);
            throw new AgentException(LumiRoseErrorCode.AGENT_CANCELLED, "运行已取消");
        } catch (LumiRoseException e) {
            log.error("run error: code={} type={}", e.getErrorCode().code(), e.getClass().getSimpleName());
            throw e;
        }
    }

    private ChatCompletionResponseParam requestWithRetry(ChatCompletionRequestParam request,
                                                         int turn,
                                                         StreamListener listener,
                                                         Control control,
                                                         boolean streaming,
                                                         boolean[] delivered) {
        int attempt = 0;
        while (true) {
            long attemptStartedAt = System.nanoTime();
            try {
                ChatCompletionResponseParam response;
                if (streaming) {
                    response = client.streamChatCompletion(request,
                            new Adapter(listener, control, turn, attemptStartedAt, delivered));
                } else {
                    response = client.chatCompletion(request);
                }
                recordRequest(turn, elapsedMillis(attemptStartedAt), true);
                return response;
            } catch (ClientException e) {
                recordRequest(turn, elapsedMillis(attemptStartedAt), false);
                if (!e.retriable() || attempt >= maxRetries || delivered[0]) {
                    throw e;
                }
                attempt++;
                long delay = RetryPolicy.delayMillis(attempt, retryBaseDelayMillis, retryMaxDelayMillis);
                recordRetry(attempt, delay, e.getErrorCode().code());
                log.debug("retry: attempt={} delayMs={} code={}",
                        attempt, delay, e.getErrorCode().code());
                backoff(delay, control);
            } catch (RuntimeException e) {
                recordRequest(turn, elapsedMillis(attemptStartedAt), false);
                throw e;
            }
        }
    }

    private void backoff(long delayMillis, Control control) {
        long deadline = System.nanoTime() + delayMillis * 1_000_000L;
        while (true) {
            checkCancelled(control);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            long slice = Math.min(50L, Math.max(1L, remaining / 1_000_000L));
            try {
                Thread.sleep(slice);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AgentException(LumiRoseErrorCode.AGENT_CANCELLED, "退避等待被中断");
            }
        }
    }

    private void executeToolCalls(ChatCompletionMessage message,
                                  List<ChatCompletionMessageParam> messages,
                                  List<ChatCompletionMessageParam> pending,
                                  int turn,
                                  List<ToolCallRecord> toolCallRecords,
                                  StreamListener listener,
                                  Control control,
                                  boolean streaming) {
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
            if (streaming) {
                listener.onToolStart(toolCall.id(), name);
            }

            long toolStartedAt = System.nanoTime();
            String result;
            try {
                result = toolContext.execute(name, toolCall.function().arguments());
            } catch (RuntimeException e) {
                long millis = elapsedMillis(toolStartedAt);
                recordTool(name, millis, false);
                if (streaming) {
                    listener.onToolEnd(toolCall.id(), name, millis, false);
                }
                log.debug("turn {} tool {} failed elapsedMs={}", turn, name, millis);
                throw e;
            }
            long toolMillis = elapsedMillis(toolStartedAt);
            recordTool(name, toolMillis, true);
            if (streaming) {
                listener.onToolEnd(toolCall.id(), name, toolMillis, true);
            }
            toolCallRecords.add(new ToolCallRecord(toolCall.id(), name, toolMillis));
            log.debug("turn {} tool {} ok elapsedMs={}", turn, name, toolMillis);

            ChatCompletionToolMessageParam toolMessage = ChatCompletionToolMessageParam.builder()
                    .toolCallId(toolCall.id())
                    .content(result == null ? "" : result)
                    .build();
            messages.add(toolMessage);
            pending.add(toolMessage);

            checkCancelled(control);
        }
    }

    private static void fireTurnEnd(StreamListener listener,
                                    boolean streaming,
                                    int turn,
                                    FinishReason finishReason,
                                    ChatCompletionResponseParam.Usage usage) {
        if (!streaming) {
            return;
        }
        listener.onTurnEnd(turn, finishReason,
                usage == null ? 0 : nullSafe(usage.promptTokens()),
                usage == null ? 0 : nullSafe(usage.completionTokens()));
    }

    private static void checkCancelled(Control control) {
        if (control != null && control.isCancelled()) {
            throw new CancelledRun();
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

    private void recordRetry(int attempt, long delayMillis, int errorCode) {
        try {
            metricsRecorder.onRetry(attempt, delayMillis, String.valueOf(errorCode));
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
        this.maxRetries = builder.maxRetries;
        this.retryBaseDelayMillis = builder.retryBaseDelayMillis;
        this.retryMaxDelayMillis = builder.retryMaxDelayMillis;
        this.contextBudgetChars = builder.contextBudgetChars;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final class Control implements StreamControl {

        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class Adapter implements CompletionStreamListener {

        private final StreamListener listener;
        private final Control control;
        private final int turn;
        private final long requestStartedAt;
        private final boolean[] delivered;
        private boolean firstDeltaLogged;

        private Adapter(StreamListener listener, Control control, int turn,
                        long requestStartedAt, boolean[] delivered) {
            this.listener = listener;
            this.control = control;
            this.turn = turn;
            this.requestStartedAt = requestStartedAt;
            this.delivered = delivered;
        }

        @Override
        public void onReasoningDelta(String delta) {
            checkCancelled(control);
            logFirstDelta();
            listener.onReasoningDelta(delta);
            checkCancelled(control);
        }

        @Override
        public void onContentDelta(String delta) {
            checkCancelled(control);
            logFirstDelta();
            listener.onContentDelta(delta);
            checkCancelled(control);
        }

        private void logFirstDelta() {
            if (!firstDeltaLogged) {
                firstDeltaLogged = true;
                delivered[0] = true;
                log.debug("first delta: turn={} firstDeltaMs={}", turn, elapsedMillis(requestStartedAt));
            }
        }
    }

    public static final class Builder {

        private LumiRoseClient client;
        private ToolContext toolContext;
        private MessageRepository repository;
        private String systemMessage = DEFAULT_SYSTEM_MESSAGE;
        private int maxTurns = 10;
        private MetricsRecorder metricsRecorder = MetricsRecorder.NOOP;
        private int maxRetries = 0;
        private long retryBaseDelayMillis = 500;
        private long retryMaxDelayMillis = 8000;
        private Integer contextBudgetChars;

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

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryBaseDelayMillis(long retryBaseDelayMillis) {
            this.retryBaseDelayMillis = retryBaseDelayMillis;
            return this;
        }

        public Builder retryMaxDelayMillis(long retryMaxDelayMillis) {
            this.retryMaxDelayMillis = retryMaxDelayMillis;
            return this;
        }

        public Builder contextBudgetChars(Integer contextBudgetChars) {
            this.contextBudgetChars = contextBudgetChars;
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
            if (maxRetries < 0) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "maxRetries 不能小于 0");
            }
            if (retryBaseDelayMillis < 0) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "retryBaseDelayMillis 不能小于 0");
            }
            if (retryMaxDelayMillis < retryBaseDelayMillis) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID,
                        "retryMaxDelayMillis 不能小于 retryBaseDelayMillis");
            }
            if (contextBudgetChars != null && contextBudgetChars <= 0) {
                throw new ConfigurationException(LumiRoseErrorCode.CONFIG_INVALID, "contextBudgetChars 必须大于 0");
            }
            return new LumiRoseAgent(this);
        }
    }
}
