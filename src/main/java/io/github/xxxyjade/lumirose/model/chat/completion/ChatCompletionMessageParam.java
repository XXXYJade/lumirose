package io.github.xxxyjade.lumirose.model.chat.completion;


public sealed interface ChatCompletionMessageParam permits
        ChatCompletionSystemMessageParam,
        ChatCompletionUserMessageParam,
        ChatCompletionAssistantMessageParam,
        ChatCompletionToolMessageParam {

    String role();
}