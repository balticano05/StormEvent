package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class IndexUsageTest extends PostgresTestSupport {

    private static final int ROWS = 20_000;

    private void seedSessions() {
        List<Object[]> batch = new ArrayList<>(ROWS);
        Instant now = Instant.now();
        Instant createdAt = now.minusSeconds(100_000);
        for (int i = 0; i < ROWS; i++) {
            batch.add(new Object[]{
                    UUID.randomUUID(),
                    Timestamp.from(createdAt),
                    Timestamp.from(createdAt),
                    Timestamp.from(now.minusSeconds(i)),
                    900,
                    "NEW"});
        }
        jdbc.batchUpdate("""
            INSERT INTO storm.session (id, last_access_at, created_at, expires_at, ttl_seconds, state)
            VALUES (?, ?, ?, ?, ?, ?)
            """, batch);
    }

    private void seedMessages(UUID sessionId) {
        List<Object[]> batch = new ArrayList<>(ROWS);
        Instant now = Instant.now();
        for (int i = 0; i < ROWS; i++) {
            batch.add(new Object[]{sessionId, "user", "search", "сообщение-" + i, Timestamp.from(now.minusSeconds(ROWS - i))});
        }
        jdbc.batchUpdate("""
            INSERT INTO storm.session_message (session_id, role, kind, text, created_at)
            VALUES (?, ?, ?, ?, ?)
            """, batch);
    }

    private void seedIdempotency() {
        List<Object[]> batch = new ArrayList<>(ROWS);
        Instant now = Instant.now();
        int expiredRows = ROWS / 20;
        for (int i = 0; i < ROWS; i++) {
            boolean expired = i >= ROWS - expiredRows;
            Instant expiresAt = expired
                    ? now.minusSeconds(i - (ROWS - expiredRows) + 1)
                    : now.plusSeconds(i);
            batch.add(new Object[]{UUID.randomUUID(), Timestamp.from(now.minusSeconds(i)), Timestamp.from(expiresAt)});
        }
        jdbc.batchUpdate("""
            INSERT INTO storm.idempotency (request_id, created_at, expires_at)
            VALUES (?, ?, ?)
            """, batch);
        jdbc.execute("ANALYZE storm.idempotency");
    }

    private void seedRequestLog(UUID sessionId) {
        List<Object[]> batch = new ArrayList<>(ROWS);
        Instant now = Instant.now();
        for (int i = 0; i < ROWS; i++) {
            batch.add(new Object[]{UUID.randomUUID(), sessionId, Timestamp.from(now.minusSeconds(i))});
        }
        jdbc.batchUpdate("""
            INSERT INTO storm.request_log (request_id, session_id, created_at, status)
            VALUES (?, ?, ?, 'OK')
            """, batch);
    }

    private String plan(String sql, Object... args) {
        return jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class, args)
                .stream()
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void expiredSessionScanUsesExpiresIndex() {
        seedSessions();

        String plan = plan("SELECT id FROM storm.session WHERE expires_at < NOW() ORDER BY expires_at LIMIT 100");

        assertTrue(plan.contains("idx_session_expires_at"), "чистка сессий должна идти по индексу:\n" + plan);
    }

    @Test
    void sessionLookupByIdUsesPrimaryKey() {
        seedSessions();
        UUID id = jdbc.queryForObject("SELECT id FROM storm.session LIMIT 1", UUID.class);

        String plan = plan("SELECT * FROM storm.session WHERE id = ?", id);

        assertTrue(plan.contains("_pkey"), "поиск сессии идёт по первичному ключу:\n" + plan);
    }

    @Test
    void contextQueryUsesSessionMessageIndex() {
        UUID sessionId = insertSession();
        seedMessages(sessionId);

        String plan = plan("""
            SELECT * FROM storm.session_message
            WHERE session_id = ?
            ORDER BY created_at DESC
            LIMIT 20
            """, sessionId);

        assertTrue(plan.contains("idx_session_message_session_created"),
                "контекст диалога берётся по составному индексу:\n" + plan);
    }

    @Test
    void idempotencyCleanupUsesExpiresIndex() {
        seedIdempotency();

        String plan = plan("""
            SELECT request_id FROM storm.idempotency
            WHERE expires_at < NOW()
            LIMIT 500
            """);

        assertTrue(plan.contains("idx_idempotency_expires_at"), "чистка окна дедупликации идёт по индексу:\n" + plan);
        assertNoSeqScan(plan, "idempotency");
    }

    @Test
    void requestLogHistoryUsesSessionIndex() {
        UUID sessionId = insertSession();
        seedRequestLog(sessionId);

        String plan = plan("SELECT * FROM storm.request_log WHERE session_id = ? ORDER BY created_at DESC", sessionId);

        assertTrue(plan.contains("request_log_default_session_id_idx"),
                "история сессии идёт по индексу партиции request_log:\n" + plan);
        assertNoSeqScan(plan, "request_log");
    }

    private void seedSourceErrors() {
        List<Object[]> batch = new ArrayList<>(ROWS);
        Instant now = Instant.now();
        List<String> sources = List.of("atlasbus", "ticketbus", "bzd", "ticketpro", "belhotel");
        List<String> codes = List.of("TIMEOUT", "PARSE_ERROR", "EMPTY_RESULT", "HTTP_5XX", "RATE_LIMITED");
        for (int i = 0; i < ROWS; i++) {
            batch.add(new Object[]{
                    UUID.randomUUID(),
                    sources.get(i % sources.size()),
                    codes.get(i / sources.size() % codes.size()),
                    "источник не ответил",
                    Timestamp.from(now.minusSeconds(i))});
        }
        jdbc.batchUpdate("""
            INSERT INTO storm.source_error_log (request_id, source, code, message, created_at)
            VALUES (?, ?, ?, ?, ?)
            """, batch);
        jdbc.execute("ANALYZE storm.source_error_log_default");
    }

    @Test
    void sourceErrorDashboardUsesSourceIndex() {
        seedSourceErrors();

        String plan = plan("""
            SELECT COUNT(*) FROM storm.source_error_log
            WHERE source = ? AND code = ? AND created_at >= NOW() - INTERVAL '1 day'
            """, "atlasbus", "RATE_LIMITED");

        assertTrue(plan.contains("source_error_log_default_source_code_created_at_idx"),
                "подсчёт ошибок источника идёт по составному индексу партиции:\n" + plan);
        assertNoSeqScan(plan, "source_error_log");
    }

    @Test
    void seedVolumeStaysWithinExpectedRows() {
        seedSessions();

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM storm.session", Integer.class);

        assertEquals(ROWS, total, "батч вставил ровно столько сессий, сколько просили");
    }

    @Test
    void batchInsertDoesNotCollapseRows() {
        UUID sessionId = insertSession();
        int size = 500;
        List<Object[]> batch = IntStream.range(0, size)
                .mapToObj(i -> new Object[]{
                        sessionId,
                        "user",
                        "search",
                        "сообщение-" + i,
                        Timestamp.from(Instant.now().minusSeconds(i))})
                .toList();

        jdbc.batchUpdate("""
            INSERT INTO storm.session_message (session_id, role, kind, text, created_at)
            VALUES (?, ?, ?, ?, ?)
            """, batch);

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM storm.session_message", Integer.class);
        Integer distinct = jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM storm.session_message", Integer.class);

        assertEquals(size, total, "батч не схлопнул строки");
        assertEquals(total, distinct, "повторяющихся строк в батче быть не должно");
    }

    private void assertNoSeqScan(String plan, String table) {
        assertFalse(plan.contains("Seq Scan on storm." + table),
                "таблица " + table + " не должна читаться последовательно:\n" + plan);
    }
}
