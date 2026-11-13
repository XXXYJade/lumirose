package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.chat.completion.ChatCompletionMessageParam;

import java.util.List;

/**
 * 消息持久层接口。
 */
public interface MessageRepository {

    /**
     * 返回会话历史的不可变快照；会话不存在时返回空列表。
     */
    List<ChatCompletionMessageParam> load(Long conversationId);

    /**
     * 原子追加一条或多条消息；空列表为空操作。
     */
    void appendAll(Long conversationId, List<ChatCompletionMessageParam> messages);
}
