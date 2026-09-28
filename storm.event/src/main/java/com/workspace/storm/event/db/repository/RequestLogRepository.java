package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.mapper.RequestLogRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RequestLogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RequestLogRowMapper rowMapper = new RequestLogRowMapper();

    public void insert(RequestLogEntity entity) {
        String sql = """
            INSERT INTO storm.request_log (request_id, session_id, text, intent_json, status, duration_ms, created_at)
            VALUES (:requestId, :sessionId, :text, :intentJson::jsonb, :status, :durationMs, :createdAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", entity.getRequestId())
                .addValue("sessionId", entity.getSessionId())
                .addValue("text", entity.getText())
                .addValue("intentJson", entity.getIntentJson())
                .addValue("status", entity.getStatus())
                .addValue("durationMs", entity.getDurationMs())
                .addValue("createdAt", entity.getCreatedAt());
        jdbc.update(sql, params);
    }

    public void batchInsert(List<RequestLogEntity> entities) {
        String sql = """
            INSERT INTO storm.request_log (request_id, session_id, text, intent_json, status, duration_ms, created_at)
            VALUES (:requestId, :sessionId, :text, :intentJson::jsonb, :status, :durationMs, :createdAt)
            """;
        MapSqlParameterSource[] batch = entities.stream().map(e -> new MapSqlParameterSource()
                .addValue("requestId", e.getRequestId())
                .addValue("sessionId", e.getSessionId())
                .addValue("text", e.getText())
                .addValue("intentJson", e.getIntentJson())
                .addValue("status", e.getStatus())
                .addValue("durationMs", e.getDurationMs())
                .addValue("createdAt", e.getCreatedAt())).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    public List<RequestLogEntity> findBySessionId(UUID sessionId) {
        String sql = "SELECT * FROM storm.request_log WHERE session_id = :sessionId ORDER BY created_at DESC";
        return jdbc.query(sql, new MapSqlParameterSource("sessionId", sessionId), rowMapper);
    }
}