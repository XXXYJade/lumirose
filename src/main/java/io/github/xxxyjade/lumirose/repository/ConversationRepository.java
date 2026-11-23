package io.github.xxxyjade.lumirose.repository;

import io.github.xxxyjade.lumirose.model.entity.Conversation;
import io.github.xxxyjade.lumirose.model.entity.Message;

import java.util.List;

/**
 * 会话持久化 SPI。
 *
 * <p>实现负责会话行创建、逻辑截断和线程安全；Agent 只依赖本接口，
 * 不接触 DO 或 Converter。没有 version 字段，并发冲突按 last-write-wins
 * 处理。
 */
public interface ConversationRepository {
    /**
     * 加载会话的 active 消息。
     *
     * <p>只返回 {@code active=true} 的消息，按消息 id 升序；不存在时返回
     * {@link Conversation#empty(long)}，且不创建会话行。
     *
     * @param conversationId 会话 id
     * @return 会话快照，永不为 {@code null}
     */
    Conversation load(long conversationId);

    /**
     * 追加 pending 消息并更新上下文 token 数。
     *
     * <p>会话行不存在时先创建；只插入 pending，不修改已有 active 消息。
     * {@code pending} 为空时直接返回，不创建会话行。
     *
     * @param conversationId 会话 id
     * @param pending        待追加消息
     * @param contextTokens  新的上下文 token 数，不能为负
     */
    void append(long conversationId, List<Message> pending, int contextTokens);

    /**
     * 用摘要和 pending 替换当前 active 消息。
     *
     * <p>会话行不存在时先创建；先把该会话全部 active 消息逻辑停用，
     * 再插入 {@code summary} 和 {@code pending}，它们都是 active。
     *
     * @param conversationId 会话 id
     * @param summary        压缩摘要
     * @param pending        压缩后保留的新消息
     * @param contextTokens  新的上下文 token 数，不能为负
     */
    void compact(long conversationId, Message summary, List<Message> pending, int contextTokens);
}
