package io.github.xxxyjade.lumirose.agent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程级会话条纹锁：同一 conversationId 串行，不同 id 并行。
 */
final class ConversationLocks {

    private static final int STRIPES = 64;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[STRIPES];

    static {
        for (int i = 0; i < STRIPES; i++) {
            LOCKS[i] = new ReentrantLock(true);
        }
    }

    private ConversationLocks() {
    }

    static ReentrantLock forId(Long conversationId) {
        return LOCKS[Math.floorMod(Long.hashCode(conversationId), STRIPES)];
    }
}
