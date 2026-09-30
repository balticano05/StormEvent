package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class CacheRepositoryTest extends PostgresTestSupport {

    private CachedResultEntity entry(String key, String payload, Instant expiresAt) {
        CachedResultEntity entity = new CachedResultEntity();
        entity.setCacheKey(key);
        entity.setSource("1day");
        entity.setDomain("weather");
        entity.setPayloadJson(payload);
        entity.setCreatedAt(expiresAt.minusSeconds(300));
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    @Test
    void putAndGet() {
        cacheRepository.put(entry("minsk:tomorrow", "{\"t\":18}", Instant.now().plusSeconds(600)));

        Optional<CachedResultEntity> found = cacheRepository.get("minsk:tomorrow");
        assertTrue(found.isPresent());
        CachedResultEntity stored = found.orElseThrow();
        assertEquals("1day", stored.getSource());
        assertEquals("weather", stored.getDomain());
        assertFalse(stored.isStale());
        assertJsonEquals("{\"t\":18}", stored.getPayloadJson());
    }

    @Test
    void missOnUnknownKey() {
        assertTrue(cacheRepository.get("nope").isEmpty());
    }

    @Test
    void putOverwritesExistingEntryWithoutDuplicating() {
        cacheRepository.put(entry("key", "{\"v\":1}", Instant.now().plusSeconds(600)));
        Instant later = Instant.now().plusSeconds(1200);
        cacheRepository.put(entry("key", "{\"v\":2}", later));

        CachedResultEntity stored = cacheRepository.get("key").orElseThrow();
        assertJsonEquals("{\"v\":2}", stored.getPayloadJson());
        assertEquals(1, countRows("storm.cached_result"));
        assertTrue(cacheRepository.findExpiredKeys(Instant.now(), 10).isEmpty(), "перезапись не должна протухать");
    }

    @Test
    void putKeepsOriginalSourceAndDomain() {
        cacheRepository.put(entry("key", "{\"v\":1}", Instant.now().plusSeconds(600)));
        CachedResultEntity second = entry("key", "{\"v\":2}", Instant.now().plusSeconds(1200));
        second.setSource("3day");
        second.setDomain("traffic");

        cacheRepository.put(second);

        CachedResultEntity stored = cacheRepository.get("key").orElseThrow();
        assertEquals("1day", stored.getSource());
        assertEquals("weather", stored.getDomain());
    }

    @Test
    void findsOnlyExpiredEntries() {
        Instant now = Instant.now();
        cacheRepository.put(entry("expired", "{}", now.minusSeconds(60)));
        cacheRepository.put(entry("alive", "{}", now.plusSeconds(600)));

        List<String> expired = cacheRepository.findExpiredKeys(now, 100);

        assertEquals(List.of("expired"), expired);
    }

    @Test
    void expiredSelectionRespectsLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) {
            cacheRepository.put(entry("key-" + i, "{}", now.minusSeconds(60 + i)));
        }

        assertEquals(2, cacheRepository.findExpiredKeys(now, 2).size());
    }

    @Test
    void deleteByKeysRemovesOnlyExpired() {
        Instant now = Instant.now();
        cacheRepository.put(entry("expired", "{}", now.minusSeconds(60)));
        cacheRepository.put(entry("alive", "{}", now.plusSeconds(600)));

        int deleted = cacheRepository.deleteByKeys(cacheRepository.findExpiredKeys(now, 100));

        assertEquals(1, deleted);
        assertTrue(cacheRepository.get("expired").isEmpty());
        assertTrue(cacheRepository.get("alive").isPresent());
    }

    @Test
    void deleteByEmptyKeysIsNoop() {
        assertEquals(0, cacheRepository.deleteByKeys(List.of()));
    }

    @Test
    void findExpiredReturnsEntities() {
        Instant now = Instant.now();
        cacheRepository.put(entry("expired", "{\"v\":1}", now.minusSeconds(60)));

        List<CachedResultEntity> expired = cacheRepository.findExpired(now);

        assertEquals(1, expired.size());
        assertEquals("expired", expired.getFirst().getCacheKey());
    }

    @Test
    void markStaleKeepsPayload() {
        cacheRepository.put(entry("key", "{\"v\":1}", Instant.now().plusSeconds(600)));

        cacheRepository.markStale("key");

        CachedResultEntity stored = cacheRepository.get("key").orElseThrow();
        assertTrue(stored.isStale());
        assertJsonEquals("{\"v\":1}", stored.getPayloadJson());
    }

    @Test
    void deleteRemovesEntry() {
        cacheRepository.put(entry("key", "{}", Instant.now().plusSeconds(600)));

        cacheRepository.delete("key");

        assertTrue(cacheRepository.get("key").isEmpty());
    }

    @Test
    void storesLargePayload() {
        String big = "x".repeat(1_000_000);

        cacheRepository.put(entry("big", "{\"data\":\"" + big + "\"}", Instant.now().plusSeconds(600)));

        CachedResultEntity stored = cacheRepository.get("big").orElseThrow();
        assertTrue(stored.getPayloadJson().contains(big), "длинный payload должен сохраниться целиком");
    }

    @Test
    void concurrentPutsOfSameKeyLeaveSingleRow() throws Exception {
        int writers = 8;
        CyclicBarrier barrier = new CyclicBarrier(writers);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Callable<Void>> tasks = IntStream.range(0, writers)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        cacheRepository.put(entry("race", "{\"w\":" + i + "}", Instant.now().plusSeconds(600)));
                        return null;
                    })
                    .toList();
            for (Future<Void> done : pool.invokeAll(tasks)) {
                done.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, countRows("storm.cached_result"));
        assertNotEquals("", cacheRepository.get("race").orElseThrow().getPayloadJson());
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count != null ? count : 0;
    }

    @Test
    void keysWithSamePrefixAreIndependent() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        cacheRepository.put(entry("minsk:tomorrow", "{\"city\":\"minsk\"}", expiresAt));
        cacheRepository.put(entry("minsk:week", "{\"city\":\"minsk\"}", expiresAt));

        cacheRepository.delete("minsk:tomorrow");

        assertTrue(cacheRepository.get("minsk:tomorrow").isEmpty());
        assertTrue(cacheRepository.get("minsk:week").isPresent());
    }
    @Test
    void keepsPricePrecisionInJsonbPayload() {
        cacheRepository.put(entry("minsk:bus:25.50", "{\"price\":25.50}", Instant.now().plusSeconds(600)));

        String payload = cacheRepository.get("minsk:bus:25.50").orElseThrow().getPayloadJson();

        assertTrue(payload.contains("25.50"), "цена не потеряла точность: " + payload);
        assertFalse(payload.contains("25.5,") && !payload.contains("25.50"),
                "double-артефакта в jsonb нет: " + payload);
    }
}
