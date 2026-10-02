package com.workspace.storm.event.metrics;

import java.util.HashMap;
import java.util.Map;

/** Локальные пороги; WARN/ERROR через AlertSink. */
public class AlertEvaluator {

    private final MetricsCollector metrics;
    private final AlertSink sink;
    private final Map<String, Long> thresholds = new HashMap<>();

    public AlertEvaluator(MetricsCollector metrics, AlertSink sink) {
        this.metrics = metrics;
        this.sink = sink;
    }

    public void setThreshold(String alertName, long threshold) {
        thresholds.put(alertName, threshold);
    }

    public void check(String alertName, String counterName) {
        Long threshold = thresholds.get(alertName);
        if (threshold == null) {
            return;
        }
        long current = metrics.counter(counterName);
        if (current > threshold) {
            sink.alert(alertName, alertName + " breached: " + current + " > " + threshold);
        }
    }
}
