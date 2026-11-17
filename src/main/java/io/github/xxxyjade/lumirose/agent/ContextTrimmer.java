package io.github.xxxyjade.lumirose.agent;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionAssistantMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionSystemMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolCall;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionToolMessageParam;
import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionUserMessageParam;

import java.util.ArrayList;
import java.util.List;

/**
 * 按字符预算裁剪请求消息。只做尽力而为的估算，不改仓储。
 */
final class ContextTrimmer {

    private ContextTrimmer() {
    }

    static int estimate(List<ChatCompletionMessageParam> messages) {
        int total = 0;
        for (ChatCompletionMessageParam message : messages) {
            total += 8;
            total += length(contentOf(message));
            for (ChatCompletionToolCall toolCall : toolCallsOf(message)) {
                ChatCompletionToolCall.Function function = toolCall.function();
                if (function != null) {
                    total += length(function.name());
                    total += length(function.arguments());
                }
            }
        }
        return total;
    }

    static List<ChatCompletionMessageParam> trim(List<ChatCompletionMessageParam> messages, int budget) {
        int total = estimate(messages);
        if (messages.size() <= 1 || total <= budget) {
            return messages;
        }

        List<ChatCompletionMessageParam> result = new ArrayList<>(messages);
        int start = "system".equals(result.get(0).role()) ? 1 : 0;

        while (total > budget) {
            int last = result.size() - 1;
            if (start >= last) {
                break;
            }

            int end = Math.min(blockEnd(result, start), last);
            total -= estimate(result.subList(start, end));
            result.subList(start, end).clear();

            while (start < result.size() - 1 && "tool".equals(result.get(start).role())) {
                total -= estimate(result.subList(start, start + 1));
                result.remove(start);
            }
        }
        return result;
    }

    private static int blockEnd(List<ChatCompletionMessageParam> messages, int start) {
        ChatCompletionMessageParam first = messages.get(start);
        if (!toolCallsOf(first).isEmpty()) {
            int index = start + 1;
            while (index < messages.size() && "tool".equals(messages.get(index).role())) {
                index++;
            }
            return index;
        }
        return start + 1;
    }

    private static String contentOf(ChatCompletionMessageParam message) {
        return switch (message) {
            case ChatCompletionSystemMessageParam system -> system.content();
            case ChatCompletionUserMessageParam user -> user.content();
            case ChatCompletionAssistantMessageParam assistant -> assistant.content();
            case ChatCompletionToolMessageParam tool -> tool.content();
        };
    }

    private static List<ChatCompletionToolCall> toolCallsOf(ChatCompletionMessageParam message) {
        if (message instanceof ChatCompletionAssistantMessageParam assistant
                && assistant.toolCalls() != null) {
            return assistant.toolCalls();
        }
        return List.of();
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}
