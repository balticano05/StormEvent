package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class RetentionCleanupTest extends PostgresTestSupport {

    private static final int TTL_SECONDS = SESSION_TTL_SECONDS;
    private static final long DAY = 24 * 3600;

    private UUID seedExpiredSession(Instant lastAccessAt) {
        var session = newSession(lastAccessAt);
        sessionRepository.insert(session);
        SessionMessageEntity message = new SessionMessageEntity();
        message.setSessionId(session.getId());
        message.setRole("user");
        message.setKind("search");
        message.setText("что будет завтра");
        message.setRequestId(UUID.randomUUID());
        message.setCreatedAt(lastAccessAt);
        sessionMessageRepository.insert(message);
        return session.getId();
    }

    @Test
    void sweepRemovesOnlyExpiredSessions() {
        Instant now = Instant.now();
        seedExpiredSession(now.minusSeconds(TTL_SECONDS + 60));
        UUID alive = insertSession();
        seedExpiredSession(now.minusSeconds(TTL_SECONDS * 2));

        int removed = sessionRepository.deleteByIds(sessionRepository.findExpiredIds(now, 100));

        assertEquals(2, removed);
        assertTrue(sessionRepository.findById(alive).isPresent());
        assertEquals(0, count("storm.session_message"), "реплики диалога удаляются каскадом");
    }

    @Test
    void sweepIsIdempotent() {
        Instant now = Instant.now();
        seedExpiredSession(now.minusSeconds(TTL_SECONDS + 60));

        assertEquals(1, sessionRepository.deleteByIds(sessionRepository.findExpiredIds(now, 100)));
        assertEquals(0, sessionRepository.deleteByIds(sessionRepository.findExpiredIds(now, 100)));
    }

    @Test
    void expiredSessionListIsBounded() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            seedExpiredSession(now.minusSeconds(TTL_SECONDS + 60L * (i + 1)));
        }

        List<UUID> firstBatch = sessionRepository.findExpiredIds(now, 2);
        assertEquals(2, firstBatch.size());

        int removed = sessionRepository.deleteByIds(firstBatch);
        assertEquals(2, removed);
        assertEquals(3, sessionRepository.findExpiredIds(now, 100).size());
    }

    @Test
    void sweepRemovesExpiredCacheAndKeepsFresh() {
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            cacheRepository.put(cache("stale-" + i, now.minusSeconds(60)));
        }
        cacheRepository.put(cache("fresh", now.plusSeconds(600)));

        int removed = cacheRepository.deleteByKeys(cacheRepository.findExpiredKeys(now, 100));

        assertEquals(3, removed);
        assertTrue(cacheRepository.get("fresh").isPresent());
        assertTrue(cacheRepository.findExpiredKeys(now, 100).isEmpty());
    }

    @Test
    void sweepRespectsCacheBatchLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) {
            cacheRepository.put(cache("stale-" + i, now.minusSeconds(60)));
        }

        assertEquals(2, cacheRepository.deleteByKeys(cacheRepository.findExpiredKeys(now, 2)));
        assertEquals(2, cacheRepository.findExpiredKeys(now, 100).size());
    }

    @Test
    void sweepTrimsDeduplicationWindow() {
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            idempotencyRepository.putIfAbsent(idempotency(now.minusSeconds(600)));
        }
        idempotencyRepository.putIfAbsent(idempotency(now));

        assertEquals(3, idempotencyRepository.deleteExpired(now, 100));
        assertEquals(1, count("storm.idempotency"));
    }

    @Test
    void sweepTrimsJournals() {
        Instant now = Instant.now();
        UUID sessionId = insertSession();
        for (int i = 0; i < 3; i++) {
            requestLogRepository.insert(requestLog(sessionId, now.minusSeconds(40 * DAY)));
            sourceErrorLogRepository.insert(sourceErrorError(now.minusSeconds(40 * DAY)));
        }
        requestLogRepository.insert(requestLog(sessionId, now));
        sourceErrorLogRepository.insert(sourceErrorError(now));

        assertEquals(3, requestLogRepository.deleteOlderThan(now.minusSeconds(30 * DAY), 1000));
        assertEquals(3, sourceErrorLogRepository.deleteOlderThan(now.minusSeconds(30 * DAY), 1000));
        assertEquals(1, count("storm.request_log"));
        assertEquals(1, count("storm.source_error_log"));
    }

    @Test
    void sweepOnEmptyDatabaseChangesNothing() {
        Instant now = Instant.now();

        assertEquals(0, sessionRepository.deleteByIds(sessionRepository.findExpiredIds(now, 100)));
        assertEquals(0, cacheRepository.deleteByKeys(cacheRepository.findExpiredKeys(now, 100)));
        assertEquals(0, idempotencyRepository.deleteExpired(now, 100));
        assertEquals(0, requestLogRepository.deleteOlderThan(now, 1000));
        assertEquals(0, sourceErrorLogRepository.deleteOlderThan(now, 1000));
    }

    private CachedResultEntity cache(String key, Instant expiresAt) {
        CachedResultEntity entity = new CachedResultEntity();
        entity.setCacheKey(key);
        entity.setSource("bzd");
        entity.setDomain("weather");
        entity.setPayloadJson("{\"t\":18}");
        entity.setCreatedAt(expiresAt.minusSeconds(300));
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    private IdempotencyEntity idempotency(Instant createdAt) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setResponseJson("{\"status\":\"pending\"}");
        entity.setCreatedAt(createdAt);
        entity.setExpiresAt(createdAt.plusSeconds(300));
        return entity;
    }

    private RequestLogEntity requestLog(UUID sessionId, Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setText("вопрос");
        entity.setIntentJson("{\"city\":\"minsk\"}");
        entity.setStatus("OK");
        entity.setDurationMs(100);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private SourceErrorLogEntity sourceErrorError(Instant createdAt) {
        SourceErrorLogEntity entity = new SourceErrorLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSource("bzd");
        entity.setCode("TIMEOUT");
        entity.setMessage("источник не ответил");
        entity.setLatencyMs(5000);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value != null ? value : 0;
    }
}
