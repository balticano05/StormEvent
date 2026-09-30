package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class IdempotencyRepositoryTest extends PostgresTestSupport {

    private static final int WINDOW_SECONDS = 300;

    private IdempotencyEntity record(UUID requestId, UUID sessionId, Instant createdAt) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setRequestId(requestId);
        entity.setResponseJson("{\"status\":\"pending\"}");
        entity.setSessionId(sessionId);
        entity.setCreatedAt(createdAt);
        entity.setExpiresAt(createdAt.plusSeconds(WINDOW_SECONDS));
        return entity;
    }

    @Test
    void firstClaimWins() {
        UUID requestId = UUID.randomUUID();
        UUID sessionId = insertSession();

        boolean claimed = idempotencyRepository.putIfAbsent(record(requestId, sessionId, Instant.now()));

        assertTrue(claimed);
        IdempotencyEntity stored = idempotencyRepository.get(requestId).orElseThrow();
        assertEquals(requestId, stored.getRequestId());
        assertEquals(sessionId, stored.getSessionId());
        assertJsonEquals("{\"status\":\"pending\"}", stored.getResponseJson());
    }

    @Test
    void secondClaimWithSameRequestIdIsRejected() {
        UUID requestId = UUID.randomUUID();
        UUID sessionId = insertSession();
        idempotencyRepository.putIfAbsent(record(requestId, sessionId, Instant.now()));

        boolean claimedAgain = idempotencyRepository.putIfAbsent(record(requestId, sessionId, Instant.now()));

        assertFalse(claimedAgain);
        assertEquals(1, countRows());
    }

    @Test
    void saveResponseFillsStoredAnswer() {
        UUID requestId = UUID.randomUUID();
        idempotencyRepository.putIfAbsent(record(requestId, insertSession(), Instant.now()));

        idempotencyRepository.saveResponse(requestId, "{\"text\":\"завтра +18\"}");

        assertJsonEquals("{\"text\":\"завтра +18\"}", idempotencyRepository.get(requestId).orElseThrow().getResponseJson());
    }

    @Test
    void missingRequestIdIsEmpty() {
        assertTrue(idempotencyRepository.get(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findExpiredIgnoresFreshWindow() {
        UUID fresh = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        UUID sessionId = insertSession();
        Instant now = Instant.now();
        idempotencyRepository.putIfAbsent(record(fresh, sessionId, now));
        idempotencyRepository.putIfAbsent(record(expired, sessionId, now.minusSeconds(WINDOW_SECONDS + 60)));

        List<IdempotencyEntity> found = idempotencyRepository.findExpired(now);

        assertEquals(1, found.size());
        assertEquals(expired, found.getFirst().getRequestId());
    }

    @Test
    void deleteExpiredRespectsLimit() {
        UUID sessionId = insertSession();
        Instant old = Instant.now().minusSeconds(WINDOW_SECONDS + 60);
        for (int i = 0; i < 3; i++) {
            idempotencyRepository.putIfAbsent(record(UUID.randomUUID(), sessionId, old));
        }
        idempotencyRepository.putIfAbsent(record(UUID.randomUUID(), sessionId, Instant.now()));

        int deleted = idempotencyRepository.deleteExpired(Instant.now(), 2);

        assertEquals(2, deleted);
        assertEquals(2, countRows());
    }

    @Test
    void deleteExpiredWithNothingToRemove() {
        assertEquals(0, idempotencyRepository.deleteExpired(Instant.now(), 100));
    }

    @Test
    void expiresAtSitsOnTheNextMonthBoundary() {
        UUID requestId = UUID.randomUUID();
        UUID sessionId = insertSession();
        Instant createdAt = Instant.parse("2026-02-01T00:00:00Z");
        IdempotencyEntity entity = record(requestId, sessionId, createdAt);
        entity.setExpiresAt(createdAt.plus(WINDOW_SECONDS, ChronoUnit.SECONDS));

        idempotencyRepository.putIfAbsent(entity);

        assertNotNull(idempotencyRepository.get(requestId).orElseThrow().getExpiresAt());
        assertEquals(1, countRows());
        assertTrue(idempotencyRepository.findExpired(createdAt).isEmpty());
        assertEquals(1, idempotencyRepository.findExpired(createdAt.plusSeconds(WINDOW_SECONDS + 1)).size());
    }

    @Test
    void anonymousRequestIsDeduplicatedToo() {
        UUID requestId = UUID.randomUUID();

        assertTrue(idempotencyRepository.putIfAbsent(record(requestId, null, Instant.now())));
        assertFalse(idempotencyRepository.putIfAbsent(record(requestId, null, Instant.now())));
        assertNull(idempotencyRepository.get(requestId).orElseThrow().getSessionId());
    }

    @Test
    void concurrentClaimsProduceExactlyOneWinner() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID sessionId = insertSession();
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return idempotencyRepository.putIfAbsent(record(requestId, sessionId, Instant.now()));
                    })
                    .toList();
            int winners = 0;
            for (Future<Boolean> done : pool.invokeAll(tasks)) {
                if (done.get(20, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, countRows());
    }

    private int countRows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM storm.idempotency", Integer.class);
        return count != null ? count : 0;
    }
}
