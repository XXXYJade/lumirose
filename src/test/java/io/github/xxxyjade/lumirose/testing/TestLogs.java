package io.github.xxxyjade.lumirose.testing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 捕获指定 logger 的日志事件。
 *
 * <p>测试日志配置的根级别是 WARN，因此这里临时把目标 logger 调到 DEBUG，
 * 结束时移除 appender 并恢复原级别，避免测试之间互相影响。
 */
public final class TestLogs implements AutoCloseable {

    private final SynchronizedAppender appender;
    private final Map<Logger, Level> previousLevels;

    private TestLogs(SynchronizedAppender appender, Map<Logger, Level> previousLevels) {
        this.appender = appender;
        this.previousLevels = previousLevels;
    }

    /**
     * 挂载 appender 并把给定类的 logger 调到 DEBUG。
     *
     * @param types 需要捕获的日志来源
     * @return 可关闭的捕获器
     */
    public static TestLogs attach(Class<?>... types) {
        SynchronizedAppender appender = new SynchronizedAppender();
        appender.start();
        Map<Logger, Level> previous = new LinkedHashMap<>();
        for (Class<?> type : types) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            previous.put(logger, logger.getLevel());
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
        }
        return new TestLogs(appender, previous);
    }

    /**
     * 返回当前已捕获事件的快照。
     *
     * @return 日志事件
     */
    public List<ILoggingEvent> events() {
        return appender.snapshot();
    }

    /**
     * 返回格式化后的消息列表。
     *
     * @return 每条日志的格式化消息
     */
    public List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * 返回所有消息拼接成的文本，便于做敏感串检查。
     *
     * @return 全部日志文本
     */
    public String allText() {
        return String.join("\n", messages());
    }

    /**
     * 判断是否存在包含指定片段且级别匹配的事件。
     *
     * @param fragment 消息片段
     * @param level    期望级别
     * @return 存在则为 {@code true}
     */
    public boolean hasEvent(String fragment, Level level) {
        for (ILoggingEvent event : events()) {
            if (event.getLevel() == level && event.getFormattedMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 轮询等待某个事件出现，用于后台线程产生的日志。
     *
     * @param fragment      消息片段
     * @param level         期望级别
     * @param timeoutMillis 超时毫秒数
     * @return 在超时前出现则为 {@code true}
     */
    public boolean awaitEvent(String fragment, Level level, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (hasEvent(fragment, level)) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return hasEvent(fragment, level);
    }

    @Override
    public void close() {
        previousLevels.forEach((logger, level) -> {
            logger.detachAppender(appender);
            logger.setLevel(level);
        });
        appender.stop();
    }

    /**
     * logback 的 ListAppender 使用非同步 ArrayList；Agent 在虚拟线程里写、
     * 测试线程里读，因此这里在写和快照时都加锁。
     */
    private static final class SynchronizedAppender extends ListAppender<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            synchronized (list) {
                super.append(event);
            }
        }

        private List<ILoggingEvent> snapshot() {
            synchronized (list) {
                return List.copyOf(list);
            }
        }
    }
}
