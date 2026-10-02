package com.workspace.storm.event.metrics;

public class NoopAlertSink implements AlertSink {

    @Override
    public void alert(String name, String message) {
    }
}
