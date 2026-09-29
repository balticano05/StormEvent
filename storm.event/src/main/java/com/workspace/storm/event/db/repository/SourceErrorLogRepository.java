package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.mapper.SourceErrorLogRowMapper;
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

    private final NamedParameterJdbcTemplate jdbc;
    private final SourceErrorLogRowMapper rowMapper = new SourceErrorLogRowMapper();

    public void insert(SourceErrorLogEntity entity) {
        String sql = """
            INSERT INTO storm.source_error_log (request_id, source, code, message, latency_ms, created_at)
            VALUES (:requestId, :source, :code, :message, :latencyMs, :createdAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestId", entity.getRequestId())
                .addValue("source", entity.getSource())
                .addValue("code", entity.getCode())
                .addValue("message", entity.getMessage())
                .addValue("latencyMs", entity.getLatencyMs())
                .addValue("createdAt", entity.getCreatedAt());
        jdbc.update(sql, params);
    }

    public void batchInsert(List<SourceErrorLogEntity> entities) {
        String sql = """
            INSERT INTO storm.source_error_log (request_id, source, code, message, latency_ms, created_at)
            VALUES (:requestId, :source, :code, :message, :latencyMs, :createdAt)
            """;
        MapSqlParameterSource[] batch = entities.stream().map(e -> new MapSqlParameterSource()
                .addValue("requestId", e.getRequestId())
                .addValue("source", e.getSource())
                .addValue("code", e.getCode())
                .addValue("message", e.getMessage())
                .addValue("latencyMs", e.getLatencyMs())
                .addValue("createdAt", e.getCreatedAt())).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    public int countSince(String source, String code, Instant since) {
        String sql = """
            SELECT COUNT(*) FROM storm.source_error_log
            WHERE source = :source AND code = :code AND created_at >= :since
            """;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("source", source)
                .addValue("code", code)
                .addValue("since", since), Integer.class);
        return count == null ? 0 : count;
    }

    public List<SourceErrorLogEntity> findBySource(String source) {
        String sql = "SELECT * FROM storm.source_error_log WHERE source = :source ORDER BY created_at DESC LIMIT 100";
        return jdbc.query(sql, new MapSqlParameterSource("source", source), rowMapper);
    }
}