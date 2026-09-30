package com.workspace.storm.event.db;

import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пул не растёт сверх лимита и конкурентный апсерт не дедлочится
 * (шаги 319-325, ADR-041).
 */
@Tag("db")
class PoolSizingAndDeadlockTest extends PostgresTestSupport {

    private static final int THREADS = 30;
    private static final int OPS_PER_THREAD = 10;
    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private com.workspace.storm.event.db.metrics.DbPoolStats poolStats;

    @Test
    void poolDoesNotGrowBeyondConfiguredMaximum() throws Exception {
        int configured = configuredMaximumPoolSize();
        int before = poolStats.snapshot().total();

        runConcurrently(() -> {
            insertSession();
            return null;
        });

        var snapshot = poolStats.snapshot();
        assertTrue(snapshot.poolStatsAvailable(), "метрики пула доступны");
        assertTrue(snapshot.total() <= configured,
                "пул не превысил лимит: " + snapshot.total() + " > " + configured);
        assertTrue(snapshot.total() >= before);
    }

    @Test
    void concurrentSessionTouchHasNoDeadlock() throws Exception {
        UUID sessionId = insertSession();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        runConcurrently(() -> {
            try {
                sessionRepository.touch(sessionId);
            } catch (RuntimeException e) {
                failure.compareAndSet(null, e);
            }
            return null;
        });

        assertNull(failure.get(), "конкурентное продление сессии не упало: " + failure.get());
        assertTrue(sessionRepository.findById(sessionId).isPresent());
    }

    @Test
    void concurrentSourceStateUpsertHasNoDeadlock() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            boolean enabled = i % 2 == 0;
            tasks.add(() -> {
                try {
                    var state = new com.workspace.storm.event.db.entity.SourceStateEntity();
                    state.setSource("bzd");
                    state.setEnabled(enabled);
                    state.setDraining(!enabled);
                    state.setUpdatedAt(java.time.Instant.now());
                    sourceStateRepository.upsert(state);
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                }
                return null;
            });
        }

        withPool(tasks);

        assertNull(failure.get(), "конкурентный ON CONFLICT не дал дедлока: " + failure.get());
        assertEquals(1, sourceStateRepository.findAll().stream()
                .filter(state -> state.getSource().equals("bzd"))
                .count(), "источник не разъехался на две строки");
    }

    private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        withPool(tasks);
    }

    private void runConcurrently(Callable<Void> task) throws Exception {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS * OPS_PER_THREAD; i++) {
            tasks.add(task);
        }
        withPool(tasks);
    }

    private void withPool(List<Callable<Void>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            for (Future<Void> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private int configuredMaximumPoolSize() {
        var snapshot = poolStats.snapshot();
        return snapshot.poolStatsAvailable() ? snapshot.maximum() : 6;
    }
}