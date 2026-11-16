package io.github.xxxyjade.lumirose.agent;

/**
 * 退避延迟计算：base · 2^(retry-1)，按 max 封顶，无抖动。
 */
final class RetryPolicy {

    private RetryPolicy() {
    }

    static long delayMillis(int retry, long base, long max) {
        long delay = base;
        for (int i = 1; i < retry; i++) {
            if (delay >= max / 2) {
                return max;
            }
            delay *= 2;
        }
        return Math.min(delay, max);
    }
}
