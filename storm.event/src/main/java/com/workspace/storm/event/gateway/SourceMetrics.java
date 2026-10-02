package com.workspace.storm.event.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Component;

@Component
public class SourceMetrics {

    private static final long MARK = System.nanoTime();

    private final ConcurrentHashMap<String, LongAdder> resultCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> latencySums = new ConcurrentHashMap<>();

    public void record(String sourceId, String status, long durationMs) {
        resultCounters.computeIfAbsent(sourceId + "|" + status, k -> new LongAdder()).increment();
        latencySums.computeIfAbsent(sourceId, k -> new LongAdder()).add(durationMs);
    }

    public long count(String sourceId, String status) {
        return resultCounters.getOrDefault(sourceId + "|" + status, new LongAdder()).sum();
    }

    public long totalLatencyMs(String sourceId) {
        return latencySums.getOrDefault(sourceId, new LongAdder()).sum();
    }

    public void clear() {
        resultCounters.clear();
        latencySums.clear();
    }
}
