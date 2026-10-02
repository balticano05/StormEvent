package com.workspace.storm.event.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MetricsCollectorTest {

    @Test
    void countersIncrement() {
        MetricsCollector m = new MetricsCollector();
        m.increment("http.requests");
        m.increment("http.requests");
        assertEquals(2, m.counter("http.requests"));
    }

    @Test
    void taggedCounterSeparates() {
        MetricsCollector m = new MetricsCollector();
        m.increment("source.errors", "source=bzd");
        m.increment("source.errors", "source=atlas");
        assertEquals(1, m.counter("source.errors{source=bzd}"));
    }

    @Test
    void histogramBuckets() {
        MetricsCollector m = new MetricsCollector();
        m.observe("db.query.duration_ms", 50);
        m.observe("db.query.duration_ms", 300);
        m.observe("db.query.duration_ms", 800);
        m.observe("db.query.duration_ms", 5000);
        assertEquals(1, m.bucketCount("db.query.duration_ms", 0));
        assertEquals(1, m.bucketCount("db.query.duration_ms", 3));
    }

    @Test
    void gaugeStoresValue() {
        MetricsCollector m = new MetricsCollector();
        m.gauge("db.connections.active", 7);
        assertEquals(7L, m.gauge("db.connections.active"));
    }

    @Test
    void alertEvaluatorFiresOverThreshold() {
        MetricsCollector m = new MetricsCollector();
        for (int i = 0; i < 6; i++) {
            m.increment("db.errors");
        }
        java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean();
        AlertSink sink = (name, message) -> fired.set(true);
        AlertEvaluator evaluator = new AlertEvaluator(m, sink);
        evaluator.setThreshold("db.errors", 5);
        evaluator.check("db.errors", "db.errors");
        assertTrue(fired.get());
    }
}
