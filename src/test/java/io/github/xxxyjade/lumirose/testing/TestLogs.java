package io.github.xxxyjade.lumirose.testing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * 测试用日志捕获：把 ListAppender 挂到框架包上，用后移除并恢复级别。
 */
public final class TestLogs {

    private static final String LOGGER_NAME = "io.github.xxxyjade.lumirose";

    private TestLogs() {
    }

    public static ListAppender<ILoggingEvent> attach() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        return appender;
    }

    public static String text(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    public static void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.detachAppender(appender);
        logger.setLevel(null);
    }
}
