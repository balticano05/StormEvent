package com.workspace.storm.event.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Счётчики/гистограммы/gauge в памяти; без Prometheus (ADR-VL-06). */
public final class MetricsCollector {

    private static final long[] BUCKETS_MS = {100, 500, 1000};

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, long[]> histograms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> gauges = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public void increment(String name, String tag) {
        counters.computeIfAbsent(name + "{" + tag + "}", k -> new LongAdder()).increment();
    }

    public long counter(String name) {
        LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }

    public void observe(String name, long durationMs) {
        long[] buckets = histograms.computeIfAbsent(name, k -> new long[BUCKETS_MS.length + 1]);
        synchronized (buckets) {
            if (durationMs < BUCKETS_MS[0]) {
                buckets[0]++;
            } else if (durationMs < BUCKETS_MS[1]) {
                buckets[1]++;
            } else if (durationMs < BUCKETS_MS[2]) {
                buckets[2]++;
            } else {
                buckets[3]++;
            }
        }
    }

    public long bucketCount(String name, int index) {
        long[] buckets = histograms.get(name);
        return buckets == null ? 0L : buckets[index];
    }

    public void gauge(String name, long value) {
        gauges.put(name, value);
    }

    public Long gauge(String name) {
        return gauges.get(name);
    }

    public Map<String, LongAdder> snapshot() {
        return new ConcurrentHashMap<>(counters);
    }
}
