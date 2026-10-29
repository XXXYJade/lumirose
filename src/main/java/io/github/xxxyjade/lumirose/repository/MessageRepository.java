package io.github.xxxyjade.lumirose.repository;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;

/**
 * 消息持久层接口
 */
public interface MessageRepository {

    void append(Long conversationId, ChatCompletionMessageParam messageParam);

    void append(Long conversationId, List<ChatCompletionMessageParam> messageParams);

    List<ChatCompletionMessageParam> load(Long conversationId);
}
