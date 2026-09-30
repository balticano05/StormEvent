package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.mapper.RequestLogRowMapper;
import com.workspace.storm.event.db.support.SqlParams;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RequestLogRepository {

    private static final String INSERT = """
        INSERT INTO storm.request_log (request_id, session_id, text, intent_json, status, duration_ms, created_at)
        VALUES (:requestId, :sessionId, :text, :intentJson::jsonb, :status, :durationMs, :createdAt)
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RequestLogRowMapper rowMapper = new RequestLogRowMapper();

    public void insert(RequestLogEntity entity) {
        jdbc.update(INSERT, params(entity));
    }

    public void batchInsert(List<RequestLogEntity> entities) {
        MapSqlParameterSource[] batch = entities.stream()
                .map(this::params)
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT, batch);
    }

    public List<RequestLogEntity> findBySessionId(UUID sessionId) {
        String sql = "SELECT * FROM storm.request_log WHERE session_id = :sessionId ORDER BY created_at DESC";
        return jdbc.query(sql, SqlParams.create().add("sessionId", sessionId).build(), rowMapper);
    }

    public int deleteOlderThan(Instant before, int limit) {
        String sql = """
            DELETE FROM storm.request_log
            WHERE (id, created_at) IN (
                SELECT id, created_at FROM storm.request_log
                WHERE created_at < :before
                LIMIT :limit
            )
            """;
        return jdbc.update(sql, SqlParams.create()
                .addInstant("before", before)
                .add("limit", limit)
                .build());
    }

    private MapSqlParameterSource params(RequestLogEntity entity) {
        return SqlParams.create()
                .add("requestId", entity.getRequestId())
                .add("sessionId", entity.getSessionId())
                .add("text", entity.getText())
                .add("intentJson", entity.getIntentJson())
                .add("status", entity.getStatus())
                .add("durationMs", entity.getDurationMs())
                .addInstant("createdAt", entity.getCreatedAt())
                .build();
    }
}
