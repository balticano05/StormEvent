package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.mapper.SourceErrorLogRowMapper;
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
public class SourceErrorLogRepository {

    private static final String INSERT = """
        INSERT INTO storm.source_error_log (request_id, source, code, message, latency_ms, created_at)
        VALUES (:requestId, :source, :code, :message, :latencyMs, :createdAt)
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final SourceErrorLogRowMapper rowMapper = new SourceErrorLogRowMapper();

    public void insert(SourceErrorLogEntity entity) {
        jdbc.update(INSERT, params(entity));
    }

    public void batchInsert(List<SourceErrorLogEntity> entities) {
        MapSqlParameterSource[] batch = entities.stream()
                .map(this::params)
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT, batch);
    }

    public int countSince(String source, String code, Instant since) {
        String sql = """
            SELECT COUNT(*) FROM storm.source_error_log
            WHERE source = :source AND code = :code AND created_at >= :since
            """;
        Integer count = jdbc.queryForObject(sql, SqlParams.create()
                .add("source", source)
                .add("code", code)
                .addInstant("since", since)
                .build(), Integer.class);
        return count != null ? count : 0;
    }

    public List<SourceErrorLogEntity> findBySource(String source) {
        String sql = "SELECT * FROM storm.source_error_log WHERE source = :source ORDER BY created_at DESC LIMIT 100";
        return jdbc.query(sql, SqlParams.create().add("source", source).build(), rowMapper);
    }

    /** Добор журнала ошибок порциями, без ожидания занятых строк (ADR-043). */
    public int deleteOlderThan(Instant before, int limit) {
        String sql = """
            DELETE FROM storm.source_error_log
            WHERE (id, created_at) IN (
                SELECT id, created_at FROM storm.source_error_log
                WHERE created_at < :before
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """;
        return jdbc.update(sql, SqlParams.create()
                .addInstant("before", before)
                .add("limit", limit)
                .build());
    }

    private MapSqlParameterSource params(SourceErrorLogEntity entity) {
        return SqlParams.create()
                .add("requestId", entity.getRequestId())
                .add("source", entity.getSource())
                .add("code", entity.getCode())
                .add("message", entity.getMessage())
                .add("latencyMs", entity.getLatencyMs())
                .addInstant("createdAt", entity.getCreatedAt())
                .build();
    }
}
