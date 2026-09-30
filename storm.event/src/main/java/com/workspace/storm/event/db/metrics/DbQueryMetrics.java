package com.workspace.storm.event.db.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Счётчики обращений к БД (ADR-039, шаги 304, 359-361).
 *
 * <p>Метрики считает аспект {@link DbQueryMetricsAspect}, Prometheus их не
 * забирает: чтение идёт через {@code /api/v1/diagnostics/db} и лог.
 * Класс потокобезопасен, состояние не персистится - при перезапуске счётчики
 * обнуляются, что для счётчиков приемлемо.
 */
@Component
@RequiredArgsConstructor
public class DbQueryMetrics {

    private final DbMetricsProperties properties;

    private final LongAdder statements = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder slowStatements = new LongAdder();
    private final LongAdder rowsRead = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();
    private final ConcurrentMap<String, LongAdder> byCall = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> failuresByCall = new ConcurrentHashMap<>();
    private final Deque<SlowStatement> slowSamples = new ArrayDeque<>();

    /** Успешный вызов репозитория. */
    public void recordSuccess(String call, long nanos, int rows) {
        statements.increment();
        totalNanos.add(nanos);
        rowsRead.add(rows);
        bump(byCall, call);
        maxNanos.accumulateAndGet(nanos, Math::max);
        if (isSlow(nanos)) {
            slowStatements.increment();
            remember(new SlowStatement(call, millis(nanos), rows, Instant.now()));
        }
    }

    /** Вызов репозитория, завершившийся исключением. */
    public void recordFailure(String call, long nanos, String errorType) {
        statements.increment();
        failures.increment();
        totalNanos.add(nanos);
        bump(byCall, call);
        bump(failuresByCall, call + " -> " + errorType);
        maxNanos.accumulateAndGet(nanos, Math::max);
        if (isSlow(nanos)) {
            slowStatements.increment();
            remember(new SlowStatement(call + " ! " + errorType, millis(nanos), 0, Instant.now()));
        }
    }

    public Snapshot snapshot() {
        long total = statements.sum();
        return new Snapshot(
                total,
                failures.sum(),
                slowStatements.sum(),
                rowsRead.sum(),
                millis(totalNanos.sum()),
                millis(maxNanos.get()),
                total == 0 ? 0.0d : (double) millis(totalNanos.sum()) / total,
                sorted(byCall),
                sorted(failuresByCall),
                slowStatements(10));
    }

    /** Медленные вызовы, новые сверху. */
    public List<SlowStatement> slowStatements(int limit) {
        synchronized (slowSamples) {
            List<SlowStatement> reversed = new ArrayList<>(slowSamples);
            java.util.Collections.reverse(reversed);
            return reversed.stream().limit(limit).toList();
        }
    }

    private boolean isSlow(long nanos) {
        return nanos >= properties.getSlowQueryMs() * 1_000_000L;
    }

    private void remember(SlowStatement sample) {
        synchronized (slowSamples) {
            slowSamples.addLast(sample);
            while (slowSamples.size() > properties.getSlowSampleSize()) {
                slowSamples.removeFirst();
            }
        }
    }

    private static void bump(ConcurrentMap<String, LongAdder> map, String key) {
        map.computeIfAbsent(key, ignored -> new LongAdder()).increment();
    }

    private static Map<String, Long> sorted(ConcurrentMap<String, LongAdder> map) {
        Map<String, Long> result = new TreeMap<>();
        map.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }

    /** Медленный вызов репозитория. */
    public record SlowStatement(String call, long durationMs, int rows, Instant at) {
    }

    /** Срез счётчиков для диагностики. */
    public record Snapshot(
            long statements,
            long failures,
            long slowStatements,
            long rowsRead,
            long totalMs,
            long maxMs,
            double avgMs,
            Map<String, Long> byCall,
            Map<String, Long> failuresByCall,
            List<SlowStatement> slow) {
    }
}