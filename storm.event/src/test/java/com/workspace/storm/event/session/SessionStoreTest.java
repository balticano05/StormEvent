package com.workspace.storm.event.session;

import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.repository.SessionMessageRepository;
import com.workspace.storm.event.db.repository.SessionRepository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SessionStoreTest {

    @Test
    void recentMessagesCapsAtContextWindow() {
        SessionRepository sessions = new SessionRepository(null) {
            @Override public Optional<SessionEntity> findById(UUID id) { return Optional.of(new SessionEntity()); }
        };
        SessionMessageRepository messages = new SessionMessageRepository(null) {
            @Override public List<SessionMessageEntity> findBySessionId(UUID sessionId, int limit) {
                return java.util.stream.IntStream.range(0, 20).mapToObj(i -> new SessionMessageEntity()).toList();
            }
        };
        SessionStore store = new SessionStore(sessions, messages);
        assertEquals(20, store.recentMessages(UUID.randomUUID()).size());
    }
}
