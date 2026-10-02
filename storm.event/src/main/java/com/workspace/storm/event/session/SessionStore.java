package com.workspace.storm.event.session;

import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.repository.SessionMessageRepository;
import com.workspace.storm.event.db.repository.SessionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Фасад над репозиториями сессии: touch продлевает TTL, append — диалог (ADR-VL-02). */
public class SessionStore {

    public static final int CONTEXT_WINDOW = 20;

    private final SessionRepository sessions;
    private final SessionMessageRepository messages;

    public SessionStore(SessionRepository sessions, SessionMessageRepository messages) {
        this.sessions = sessions;
        this.messages = messages;
    }

    public Optional<SessionEntity> get(UUID id) {
        return sessions.findById(id);
    }

    public void put(SessionEntity entity) {
        sessions.insert(entity);
    }

    public int touch(UUID id) {
        return sessions.touch(id);
    }

    public void appendMessage(SessionMessageEntity message) {
        messages.insert(message);
    }

    public List<SessionMessageEntity> recentMessages(UUID sessionId) {
        return messages.findBySessionId(sessionId, CONTEXT_WINDOW);
    }
}
