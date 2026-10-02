package com.workspace.storm.event.metrics;

public interface AlertSink {

    void alert(String name, String message);
}
