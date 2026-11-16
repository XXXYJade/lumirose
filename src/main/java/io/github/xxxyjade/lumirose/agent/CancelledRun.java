package io.github.xxxyjade.lumirose.agent;

/**
 * 内部信号：取消发生在读取循环内部时用于打断 transport，不对外暴露。
 */
final class CancelledRun extends RuntimeException {

    CancelledRun() {
        super(null, null, false, false);
    }
}
