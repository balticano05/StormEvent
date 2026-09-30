package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.mapper.IdempotencyRowMapper;
import com.workspace.storm.event.db.support.SqlParams;
import lombok.RequiredArgsConstructor;
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
        return jdbc.query(sql, SqlParams.create().add("requestId", requestId).build(), rowMapper)
                .stream().findFirst();
    }

    public boolean putIfAbsent(IdempotencyEntity entity) {
        String sql = """
            INSERT INTO storm.idempotency (request_id, response_json, session_id, created_at, expires_at)
            VALUES (:requestId, :responseJson::jsonb, :sessionId, :createdAt, :expiresAt)
            ON CONFLICT (request_id) DO NOTHING
            """;
        int rows = jdbc.update(sql, SqlParams.create()
                .add("requestId", entity.getRequestId())
                .add("responseJson", entity.getResponseJson())
                .add("sessionId", entity.getSessionId())
                .addInstant("createdAt", entity.getCreatedAt())
                .addInstant("expiresAt", entity.getExpiresAt())
                .build());
        return rows > 0;
    }

    public void saveResponse(UUID requestId, String responseJson) {
        String sql = "UPDATE storm.idempotency SET response_json = :responseJson::jsonb WHERE request_id = :requestId";
        jdbc.update(sql, SqlParams.create()
                .add("requestId", requestId)
                .add("responseJson", responseJson)
                .build());
    }

    public List<IdempotencyEntity> findExpired(Instant before) {
        String sql = "SELECT * FROM storm.idempotency WHERE expires_at < :before";
        return jdbc.query(sql, SqlParams.create().addInstant("before", before).build(), rowMapper);
    }

    /**
     * Чистит окно дедупликации порциями по {@code limit} (ADR-043).
     *
     * <p>Одна операция вместо «выбрали ключи, потом удалили»: пара операций
     * на одной и той же строке из двух сборщиков давала взаимную блокировку и
     * лишний сетевой заход. {@code FOR UPDATE SKIP LOCKED} оставляет занятые
     * строки следующему проходу.
     */
    public int deleteExpired(Instant before, int limit) {
        String sql = """
            DELETE FROM storm.idempotency
            WHERE request_id IN (
                SELECT request_id FROM storm.idempotency
                WHERE expires_at < :before
                ORDER BY expires_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """;
        return jdbc.update(sql, SqlParams.create()
                .addInstant("before", before)
                .add("limit", limit)
                .build());
    }
}
