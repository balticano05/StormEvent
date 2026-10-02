package com.workspace.storm.event.dto.offer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Component;

@Component
public class OfferMetrics {

    private final ConcurrentHashMap<String, LongAdder> normalized = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> dropped = new ConcurrentHashMap<>();

    public void recordNormalized(String source) {
        normalized.computeIfAbsent(source, k -> new LongAdder()).increment();
    }

    public void recordDropped(String reason) {
        dropped.computeIfAbsent(reason, k -> new LongAdder()).increment();
    }

    public long normalizedCount(String source) {
        return normalized.getOrDefault(source, new LongAdder()).sum();
    }

    public long droppedCount(String reason) {
        return dropped.getOrDefault(reason, new LongAdder()).sum();
    }

    public void clear() {
        normalized.clear();
        dropped.clear();
    }
}
