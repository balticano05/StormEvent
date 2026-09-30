package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.entity.SourceStateEntity;
import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class TransactionalWriteTest extends PostgresTestSupport {

    private SessionMessageEntity message(UUID sessionId, Instant createdAt) {
        SessionMessageEntity entity = new SessionMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setKind("search");
        entity.setText("что будет завтра");
        entity.setRequestId(UUID.randomUUID());
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void commitKeepsSessionMessagesAndLogTogether() {
        UUID sessionId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var session = newSession(Instant.now());
            session.setId(sessionId);
            sessionRepository.insert(session);
            sessionMessageRepository.insert(message(sessionId, Instant.now()));
            requestLogRepository.insert(logEntry(sessionId, Instant.now()));
        });

        assertEquals(1, count("storm.session"));
        assertEquals(1, sessionMessageRepository.findBySessionId(sessionId, 10).size());
        assertEquals(1, requestLogRepository.findBySessionId(sessionId).size());
    }

    @Test
    void rollbackLeavesNoHalfWrittenDialog() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        assertThrows(RuntimeException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    sessionRepository.insert(newSession(now));
                    sessionMessageRepository.insert(message(sessionId, now));
                }));

        assertEquals(0, count("storm.session"), "сессия без сообщений не должна оставаться");
    }

    @Test
    void constraintViolationRollsBackWholeBatch() {
        Instant now = Instant.now();
        List<RequestLogEntity> batch = List.of(
                logEntry(null, now),
                logEntry(null, now.plusSeconds(1)),
                logEntry(null, now.plusSeconds(2)));

        assertThrows(DataIntegrityViolationException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> requestLogRepository.batchInsert(withBrokenStatus(batch))));

        assertEquals(0, count("storm.request_log"), "падающий batch не оставляет частичных строк");
    }

    @Test
    void nestedFailureDoesNotPoisonOuterTransaction() {
        Instant now = Instant.now();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var session = newSession(now);
            sessionRepository.insert(session);
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(inner -> {
                    sessionMessageRepository.insert(message(session.getId(), now));
                    throw new IllegalStateException("сбой вложенной операции");
                });
            } catch (IllegalStateException expected) {
                status.setRollbackOnly();
            }
        });

        assertEquals(0, count("storm.session_message"));
        assertEquals(0, count("storm.session"), "внешняя транзакция помечена на откат целиком");
    }

    @Test
    void idempotencyClaimAndAnswerStayInOneTransaction() {
        UUID requestId = UUID.randomUUID();
        UUID sessionId = insertSession();
        Instant now = Instant.now();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var record = new IdempotencyEntity();
            record.setRequestId(requestId);
            record.setSessionId(sessionId);
            record.setCreatedAt(now);
            record.setExpiresAt(now.plusSeconds(300));
            assertTrue(idempotencyRepository.putIfAbsent(record));
            idempotencyRepository.saveResponse(requestId, "{\"text\":\"завтра +18\"}");
        });

        assertJsonEquals("{\"text\":\"завтра +18\"}",
                idempotencyRepository.get(requestId).orElseThrow().getResponseJson());
    }

    @Test
    void upsertIsAtomicPerRow() {
        Instant now = Instant.now();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var state = new SourceStateEntity();
            state.setSource("bzd");
            state.setEnabled(false);
            state.setDraining(true);
            state.setUpdatedAt(now);
            sourceStateRepository.upsert(state);
        });

        var stored = sourceStateRepository.findBySource("bzd").orElseThrow();
        assertFalse(stored.isEnabled());
        assertTrue(stored.isDraining());
        assertEquals(5, sourceStateRepository.findAll().size(), "upsert не плодит дубликаты");
    }

    private List<RequestLogEntity> withBrokenStatus(List<RequestLogEntity> entries) {
        return entries.stream().peek(entry -> entry.setStatus("WAT")).toList();
    }

    private RequestLogEntity logEntry(UUID sessionId, Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setText("вопрос");
        entity.setIntentJson("{\"city\":\"minsk\"}");
        entity.setStatus("OK");
        entity.setDurationMs(50);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value != null ? value : 0;
    }
}
