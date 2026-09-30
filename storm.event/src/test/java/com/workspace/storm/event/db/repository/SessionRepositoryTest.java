package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class SessionRepositoryTest extends PostgresTestSupport {

    private static final int TTL_SECONDS = 900;

    private UUID insertSession(Instant lastAccessAt) {
        UUID id = UUID.randomUUID();
        SessionEntity entity = new SessionEntity();
        entity.setId(id);
        entity.setIntent("{\"from\":\"minsk\"}");
        entity.setLastAccessAt(lastAccessAt);
        entity.setCreatedAt(lastAccessAt);
        entity.setExpiresAt(lastAccessAt.plusSeconds(TTL_SECONDS));
        entity.setTtlSeconds(TTL_SECONDS);
        entity.setState("NEW");
        sessionRepository.insert(entity);
        return id;
    }

    @Test
    void insertsAndReadsSession() {
        UUID id = insertSession(Instant.now());

        Optional<SessionEntity> found = sessionRepository.findById(id);
        assertTrue(found.isPresent());
        SessionEntity session = found.orElseThrow();
        assertEquals(id, session.getId());
        assertEquals(TTL_SECONDS, session.getTtlSeconds());
        assertEquals("NEW", session.getState());
        assertJsonEquals("{\"from\":\"minsk\"}", session.getIntent());
    }

    @Test
    void missingSessionIsEmpty() {
        assertTrue(sessionRepository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void updatesIntentAndState() {
        UUID id = insertSession(Instant.now());

        sessionRepository.updateIntent(id, "{\"from\":\"gomel\"}");
        sessionRepository.updateState(id, "ACTIVE");

        SessionEntity session = sessionRepository.findById(id).orElseThrow();
        assertJsonEquals("{\"from\":\"gomel\"}", session.getIntent());
        assertEquals("ACTIVE", session.getState());
    }

    @Test
    void touchExtendsLifeAndKeepsIntent() {
        UUID id = insertSession(Instant.now().minusSeconds(TTL_SECONDS));
        Instant expiresBefore = sessionRepository.findById(id).orElseThrow().getExpiresAt();

        sessionRepository.touch(id);

        SessionEntity session = sessionRepository.findById(id).orElseThrow();
        assertNotEquals(expiresBefore, session.getExpiresAt());
        assertTrue(session.getExpiresAt().isAfter(Instant.now()), "touch must push expiry into the future");
        assertJsonEquals("{\"from\":\"minsk\"}", session.getIntent());
    }

    @Test
    void findsOnlyExpiredSessions() {
        Instant now = Instant.now();
        UUID expired = insertSession(now.minusSeconds(TTL_SECONDS + 60));
        UUID alive = insertSession(now);

        assertEquals(List.of(expired), sessionRepository.findExpiredIds(now, 100));
        assertTrue(sessionRepository.findById(alive).isPresent());
    }

    @Test
    void expiredSelectionRespectsLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            insertSession(now.minusSeconds(TTL_SECONDS + 60));
        }

        assertEquals(2, sessionRepository.findExpiredIds(now, 2).size());
    }

    @Test
    void deletesExpiredBatchOnly() {
        Instant now = Instant.now();
        UUID first = insertSession(now.minusSeconds(TTL_SECONDS + 60));
        UUID second = insertSession(now.minusSeconds(TTL_SECONDS + 120));
        UUID alive = insertSession(now);

        int deleted = sessionRepository.deleteByIds(sessionRepository.findExpiredIds(now, 100));

        assertEquals(2, deleted);
        assertTrue(sessionRepository.findById(first).isEmpty());
        assertTrue(sessionRepository.findById(alive).isPresent());
    }

    @Test
    void deleteByEmptyListIsNoop() {
        assertEquals(0, sessionRepository.deleteByIds(List.of()));
    }

    @Test
    void deletesSession() {
        UUID id = insertSession(Instant.now());

        sessionRepository.delete(id);

        assertTrue(sessionRepository.findById(id).isEmpty());
    }
}
