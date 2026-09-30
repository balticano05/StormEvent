package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class RequestLogRepositoryTest extends PostgresTestSupport {

    private RequestLogEntity entry(UUID sessionId, UUID requestId, String text, Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setSessionId(sessionId);
        entity.setText(text);
        entity.setIntentJson("{\"city\":\"minsk\"}");
        entity.setStatus("OK");
        entity.setDurationMs(120);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void insertAndRead() {
        UUID sessionId = insertSession();
        UUID requestId = UUID.randomUUID();

        requestLogRepository.insert(entry(sessionId, requestId, "что будет завтра", Instant.now()));

        List<RequestLogEntity> found = requestLogRepository.findBySessionId(sessionId);
        assertEquals(1, found.size());
        RequestLogEntity stored = found.getFirst();
        assertEquals(requestId, stored.getRequestId());
        assertEquals("что будет завтра", stored.getText());
        assertEquals("OK", stored.getStatus());
        assertEquals(120, stored.getDurationMs());
        assertJsonEquals("{\"city\":\"minsk\"}", stored.getIntentJson());
    }

    @Test
    void sessionIdIsNullableForAnonymousRequests() {
        RequestLogEntity entity = entry(null, UUID.randomUUID(), "анонимный вопрос", Instant.now());

        requestLogRepository.insert(entity);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM storm.request_log WHERE session_id IS NULL", Integer.class);
        assertEquals(1, count != null ? count : 0);
    }

    @Test
    void failedRequestKeepsStatusAndNullDuration() {
        UUID sessionId = insertSession();
        RequestLogEntity entity = entry(sessionId, UUID.randomUUID(), "а где лифт", Instant.now());
        entity.setStatus("ERROR");
        entity.setDurationMs(null);

        requestLogRepository.insert(entity);

        RequestLogEntity stored = requestLogRepository.findBySessionId(sessionId).getFirst();
        assertEquals("ERROR", stored.getStatus());
        assertNull(stored.getDurationMs());
    }

    @Test
    void batchInsertWritesAllRows() {
        UUID sessionId = insertSession();
        Instant now = Instant.now();
        List<RequestLogEntity> batch = IntStream.range(0, 50)
                .mapToObj(i -> entry(sessionId, UUID.randomUUID(), "вопрос-" + i, now.plusSeconds(i)))
                .toList();

        requestLogRepository.batchInsert(batch);

        assertEquals(50, requestLogRepository.findBySessionId(sessionId).size());
    }

    @Test
    void batchInsertOfEmptyListIsNoop() {
        UUID sessionId = insertSession();

        requestLogRepository.batchInsert(List.of());

        assertTrue(requestLogRepository.findBySessionId(sessionId).isEmpty());
    }

    @Test
    void historyIsNewestFirst() {
        UUID sessionId = insertSession();
        Instant now = Instant.now();
        requestLogRepository.insert(entry(sessionId, UUID.randomUUID(), "old", now.minusSeconds(60)));
        requestLogRepository.insert(entry(sessionId, UUID.randomUUID(), "new", now));

        assertEquals("new", requestLogRepository.findBySessionId(sessionId).getFirst().getText());
    }

    @Test
    void auditLogSurvivesSessionDeletionWithoutSessionLink() {
        UUID sessionId = insertSession();
        UUID requestId = UUID.randomUUID();
        requestLogRepository.insert(entry(sessionId, requestId, "вопрос", Instant.now()));

        sessionRepository.delete(sessionId);

        Integer kept = jdbc.queryForObject(
                "SELECT COUNT(*) FROM storm.request_log WHERE request_id = ? AND session_id IS NULL",
                Integer.class, requestId);
        assertEquals(1, kept != null ? kept : 0);
    }

    @Test
    void retentionDeleteRemovesOnlyOldRows() {
        UUID sessionId = insertSession();
        Instant now = Instant.now();
        requestLogRepository.insert(entry(sessionId, UUID.randomUUID(), "old", now.minusSeconds(40 * 24 * 3600)));
        requestLogRepository.insert(entry(sessionId, UUID.randomUUID(), "fresh", now));

        int deleted = requestLogRepository.deleteOlderThan(now.minusSeconds(30 * 24 * 3600), 1000);

        assertEquals(1, deleted);
        List<RequestLogEntity> left = requestLogRepository.findBySessionId(sessionId);
        assertEquals(1, left.size());
        assertEquals("fresh", left.getFirst().getText());
    }

    @Test
    void retentionDeleteRespectsLimit() {
        UUID sessionId = insertSession();
        Instant old = Instant.now().minusSeconds(40 * 24 * 3600);
        requestLogRepository.batchInsert(IntStream.range(0, 5)
                .mapToObj(i -> entry(sessionId, UUID.randomUUID(), "old-" + i, old))
                .toList());

        assertEquals(2, requestLogRepository.deleteOlderThan(old.plusSeconds(1), 2));
        assertEquals(3, requestLogRepository.findBySessionId(sessionId).size());
    }

    @Test
    void retentionDeleteOnEmptyTable() {
        assertEquals(0, requestLogRepository.deleteOlderThan(Instant.now(), 100));
    }

    @Test
    void storesLongTextAndQuotedUserInput() {
        UUID sessionId = insertSession();
        String hostile = "'; DROP TABLE storm.request_log; -- " + "ы".repeat(50_000);

        requestLogRepository.insert(entry(sessionId, UUID.randomUUID(), hostile, Instant.now()));

        assertEquals(hostile, requestLogRepository.findBySessionId(sessionId).getFirst().getText());
        assertEquals(1, countRows());
    }

    @Test
    void intentJsonIsStoredAsStructuredColumn() {
        UUID sessionId = insertSession();
        RequestLogEntity entity = entry(sessionId, UUID.randomUUID(), "дождь?", Instant.now());
        entity.setIntentJson("{\"slots\":{\"city\":\"brest\"},\"rain\":true}");

        requestLogRepository.insert(entity);

        String rain = jdbc.queryForObject(
                "SELECT intent_json->>'rain' FROM storm.request_log WHERE session_id = ?", String.class, sessionId);
        assertEquals("true", rain);
    }

    private int countRows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM storm.request_log", Integer.class);
        return count != null ? count : 0;
    }
}
