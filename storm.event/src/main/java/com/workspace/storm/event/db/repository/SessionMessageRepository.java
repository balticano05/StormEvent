package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.mapper.SessionMessageRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionId", entity.getSessionId())
                .addValue("role", entity.getRole())
                .addValue("kind", entity.getKind())
                .addValue("text", entity.getText())
                .addValue("requestId", entity.getRequestId())
                .addValue("createdAt", entity.getCreatedAt());
        jdbc.update(sql, params);
    }

    public List<SessionMessageEntity> findBySessionId(UUID sessionId, int limit) {
        String sql = """
            SELECT * FROM storm.session_message
            WHERE session_id = :sessionId
            ORDER BY created_at DESC
            LIMIT :limit
            """;
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("limit", limit), rowMapper);
    }

    public void deleteBySessionId(UUID sessionId) {
        String sql = "DELETE FROM storm.session_message WHERE session_id = :sessionId";
        jdbc.update(sql, new MapSqlParameterSource("sessionId", sessionId));
    }
}