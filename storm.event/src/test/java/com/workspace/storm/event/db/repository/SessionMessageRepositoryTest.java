package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class SessionMessageRepositoryTest extends PostgresTestSupport {

    private SessionMessageEntity message(UUID sessionId, String text, Instant createdAt) {
        SessionMessageEntity entity = new SessionMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setKind("search");
        entity.setText(text);
        entity.setRequestId(UUID.randomUUID());
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void insertsAndReadsMessage() {
        UUID sessionId = insertSession();
        UUID requestId = UUID.randomUUID();
        SessionMessageEntity entity = message(sessionId, "что будет завтра", Instant.now());
        entity.setRequestId(requestId);

        sessionMessageRepository.insert(entity);

        List<SessionMessageEntity> found = sessionMessageRepository.findBySessionId(sessionId, 10);
        assertEquals(1, found.size());
        SessionMessageEntity stored = found.getFirst();
        assertNotNull(stored.getId());
        assertEquals(requestId, stored.getRequestId());
        assertEquals("user", stored.getRole());
        assertEquals("что будет завтра", stored.getText());
    }

    @Test
    void contextIsNewestFirstAndLimited() {
        UUID sessionId = insertSession();
        Instant base = Instant.now().minusSeconds(300);
        for (int i = 0; i < 5; i++) {
            sessionMessageRepository.insert(message(sessionId, "msg-" + i, base.plusSeconds(i)));
        }

        List<SessionMessageEntity> found = sessionMessageRepository.findBySessionId(sessionId, 3);

        assertEquals(3, found.size());
        assertEquals("msg-4", found.get(0).getText());
        assertEquals("msg-2", found.get(2).getText());
    }

    @Test
    void contextOfEmptySessionIsEmpty() {
        UUID sessionId = insertSession();

        assertTrue(sessionMessageRepository.findBySessionId(sessionId, 10).isEmpty());
    }

    @Test
    void messagesAreScopedToTheirSession() {
        UUID first = insertSession();
        UUID second = insertSession();
        sessionMessageRepository.insert(message(first, "first", Instant.now()));
        sessionMessageRepository.insert(message(second, "second", Instant.now()));

        assertEquals(1, sessionMessageRepository.findBySessionId(first, 10).size());
        assertEquals("first", sessionMessageRepository.findBySessionId(first, 10).getFirst().getText());
    }

    @Test
    void deletingSessionCascadesToMessages() {
        UUID sessionId = insertSession();
        sessionMessageRepository.insert(message(sessionId, "hi", Instant.now()));

        sessionRepository.delete(sessionId);

        assertTrue(sessionMessageRepository.findBySessionId(sessionId, 10).isEmpty());
    }

    @Test
    void explicitDeleteRemovesOnlyItsSession() {
        UUID first = insertSession();
        UUID second = insertSession();
        sessionMessageRepository.insert(message(first, "first", Instant.now()));
        sessionMessageRepository.insert(message(second, "second", Instant.now()));

        sessionMessageRepository.deleteBySessionId(first);

        assertTrue(sessionMessageRepository.findBySessionId(first, 10).isEmpty());
        assertEquals(1, sessionMessageRepository.findBySessionId(second, 10).size());
    }

    @Test
    void messageWithoutSessionIsRejectedByForeignKey() {
        SessionMessageEntity orphan = message(UUID.randomUUID(), "hi", Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> sessionMessageRepository.insert(orphan));
    }

    @Test
    void storesLongTextWithoutTruncation() {
        UUID sessionId = insertSession();
        String longText = "а".repeat(100_000);

        sessionMessageRepository.insert(message(sessionId, longText, Instant.now()));

        assertEquals(longText, sessionMessageRepository.findBySessionId(sessionId, 1).getFirst().getText());
    }
}
