package io.github.xxxyjade.lumirose.repository;

/**
 * 用接口契约验证内存实现。
 */
class InMemoryRepositoryContractTest extends RepositoryContract {

    @Override
    protected MessageRepository newRepository() {
        return new InMemoryMessageRepository();
    }
}
