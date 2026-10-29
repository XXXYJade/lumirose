package io.github.xxxyjade.lumirose.toolkit;

import com.openai.models.chat.completions.*;

public final class MessageFactory {

    private MessageFactory() {}

    public static ChatCompletionMessageParam user(String content) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(content)
                        .build()
        );
    }

    public static ChatCompletionMessageParam system(String content) {
        return ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(content)
                        .build()
        );
    }

    public static ChatCompletionMessageParam assistant(ChatCompletionMessage message) {
        return ChatCompletionMessageParam.ofAssistant(message.toParam());
    }

    public static ChatCompletionMessageParam assistant(String content) {
        return ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(content)
                        .build()

        );
    }

    public static ChatCompletionMessageParam tool(String toolCallId, Object content) {
        return ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCallId)
                        .contentAsJson(content)
                        .build()
        );
    }

}
