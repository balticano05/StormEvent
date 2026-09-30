package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.mapper.SessionMessageRowMapper;
import com.workspace.storm.event.db.support.SqlParams;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SessionMessageRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionMessageRowMapper rowMapper = new SessionMessageRowMapper();

    public void insert(SessionMessageEntity entity) {
        String sql = """
            INSERT INTO storm.session_message (session_id, role, kind, text, request_id, created_at)
            VALUES (:sessionId, :role, :kind, :text, :requestId, :createdAt)
            """;
        jdbc.update(sql, SqlParams.create()
                .add("sessionId", entity.getSessionId())
                .add("role", entity.getRole())
                .add("kind", entity.getKind())
                .add("text", entity.getText())
                .add("requestId", entity.getRequestId())
                .addInstant("createdAt", entity.getCreatedAt())
                .build());
    }

    public List<SessionMessageEntity> findBySessionId(UUID sessionId, int limit) {
        String sql = """
            SELECT * FROM storm.session_message
            WHERE session_id = :sessionId
            ORDER BY created_at DESC
            LIMIT :limit
            """;
        return jdbc.query(sql, SqlParams.create()
                .add("sessionId", sessionId)
                .add("limit", limit)
                .build(), rowMapper);
    }

    public void deleteBySessionId(UUID sessionId) {
        String sql = "DELETE FROM storm.session_message WHERE session_id = :sessionId";
        jdbc.update(sql, SqlParams.create().add("sessionId", sessionId).build());
    }
}
