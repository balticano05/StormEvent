package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.mapper.IdempotencyRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final IdempotencyRowMapper rowMapper = new IdempotencyRowMapper();

    public Optional<IdempotencyEntity> get(UUID requestId) {
        String sql = "SELECT * FROM storm.idempotency WHERE request_id = :requestId";
        List<IdempotencyEntity> result = jdbc.query(sql, new MapSqlParameterSource("requestId", requestId), rowMapper);
        return result.stream().findFirst();
    }

    public boolean putIfAbsent(IdempotencyEntity entity) {
        String sql = """
            INSERT INTO storm.idempotency (request_id, response_json, session_id, created_at, expires_at)
            VALUES (:requestId, :responseJson::jsonb, :sessionId, :createdAt, :expiresAt)
            ON CONFLICT (request_id) DO NOTHING
            """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("requestId", entity.getRequestId())
                .addValue("responseJson", entity.getResponseJson())
                .addValue("sessionId", entity.getSessionId())
                .addValue("createdAt", entity.getCreatedAt())
                .addValue("expiresAt", entity.getExpiresAt()));
        return rows > 0;
    }

    public List<IdempotencyEntity> findExpired(Instant before) {
        String sql = "SELECT * FROM storm.idempotency WHERE expires_at < :before";
        return jdbc.query(sql, new MapSqlParameterSource("before", before), rowMapper);
    }

    public void deleteExpired(Instant before) {
        String sql = "DELETE FROM storm.idempotency WHERE expires_at < :before";
        jdbc.update(sql, new MapSqlParameterSource("before", before));
    }
}