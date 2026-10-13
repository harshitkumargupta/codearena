package com.codearena.worker.judge;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    String text() {
        synchronized (appender) {
            return appender.list.stream()
                    .map(event -> event.getFormattedMessage()
                            + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                    .collect(Collectors.joining("\n"));
        }
    }

    int size() {
        synchronized (appender) {
            return appender.list.size();
        }
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
