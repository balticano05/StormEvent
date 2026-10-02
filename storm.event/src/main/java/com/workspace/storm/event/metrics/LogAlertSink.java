package com.workspace.storm.event.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogAlertSink implements AlertSink {

    private static final Logger log = LoggerFactory.getLogger(LogAlertSink.class);

    @Override
    public void alert(String name, String message) {
        log.warn("ALERT {}: {}", name, message);
    }
}
