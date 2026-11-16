package io.github.xxxyjade.lumirose.agent;

/**
 * 运行取消句柄。可在任意线程调用；重复调用幂等。
 */
public interface StreamControl {

    void cancel();

    boolean isCancelled();
}
