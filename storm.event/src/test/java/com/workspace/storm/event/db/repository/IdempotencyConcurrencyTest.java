package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Один и тот же запрос, пришедший дважды одновременно, обрабатывается один
 * раз: {@code ON CONFLICT} вместо чтения-затем-записи (шаг 342).
 */
@Tag("db")
class IdempotencyConcurrencyTest extends PostgresTestSupport {

    private static final int THREADS = 16;

    @Test
    void concurrentPutIfAbsentWinsExactlyOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        AtomicInteger winners = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                if (idempotencyRepository.putIfAbsent(pending(requestId))) {
                    winners.incrementAndGet();
                }
                return null;
            });
        }

        runConcurrently(tasks);

        assertEquals(1, winners.get(), "ответ по запросу пишет ровно один поток");
        assertEquals(1, count("storm.idempotency"));
        assertTrue(idempotencyRepository.get(requestId).isPresent());
    }

    @Test
    void savedResponseIsSharedByAllCallers() throws Exception {
        UUID requestId = UUID.randomUUID();
        idempotencyRepository.putIfAbsent(pending(requestId));

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                idempotencyRepository.saveResponse(requestId, "{\"answer\":\"18:40\"}");
                return null;
            });
        }

        runConcurrently(tasks);

        assertTrue(idempotencyRepository.get(requestId)
                .filter(row -> row.getResponseJson() != null)
                .isPresent(), "ответ виден всем, кто пришёл следом");
    }

    @Test
    void concurrentCachePutKeepsSingleRow() throws Exception {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                cacheRepository.put(cached("bus-schedule-minsk"));
                return null;
            });
        }

        runConcurrently(tasks);

        assertEquals(1, count("storm.cached_result"));
        assertTrue(cacheRepository.getFresh("bus-schedule-minsk", Instant.now()).isPresent());
    }

    @Test
    void expiredCacheIsNotReturned() {
        cacheRepository.put(cached("bus-schedule-minsk"));

        assertTrue(cacheRepository.getFresh("bus-schedule-minsk", Instant.now()).isPresent());
        assertTrue(cacheRepository.getFresh("bus-schedule-minsk", Instant.now().plusSeconds(3600)).isEmpty(),
                "просроченный кэш не отдаём");
        assertTrue(cacheRepository.get("bus-schedule-minsk").isPresent(),
                "но в диагностике он виден");
    }

    @Test
    void twoHundredParallelCacheReadsAllHitTheSameRow() throws Exception {
        cacheRepository.put(cached("bus-schedule-minsk"));
        int readers = 200;
        AtomicInteger hits = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < readers; i++) {
            tasks.add(() -> {
                if (cacheRepository.getFresh("bus-schedule-minsk", Instant.now()).isPresent()) {
                    hits.incrementAndGet();
                }
                return null;
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(24);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(executor.submit(task)));
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }

        assertEquals(readers, hits.get(), "все 200 параллельных чтений получили строку из БД");
        assertEquals(1, count("storm.cached_result"));
    }

    private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(executor.submit(task)));
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }
    }

    private IdempotencyEntity pending(UUID requestId) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setRequestId(requestId);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        return entity;
    }

    private CachedResultEntity cached(String key) {
        CachedResultEntity entity = new CachedResultEntity();
        entity.setCacheKey(key);
        entity.setSource("bzd");
        entity.setDomain("weather");
        entity.setPayloadJson("{\"t\":18}");
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(600));
        return entity;
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}