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
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RequestLogRepository {

    private static final String INSERT = """
        INSERT INTO storm.request_log (request_id, session_id, text, intent_json, status, duration_ms, created_at)
        VALUES (:requestId, :sessionId, :text, :intentJson::jsonb, :status, :durationMs, :createdAt)
        RETURNING id
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RequestLogRowMapper rowMapper = new RequestLogRowMapper();

    /**
     * Пишет запись аудита и возвращает её {@code id}.
     *
     * <p>{@code RETURNING id} вместо отдельного {@code SELECT}: идентификатор
     * нужен в логах разбора конкретного запроса, а лишний запрос к журналу на
     * каждом пользовательском запросе - это ровно тот трафик, которого мы
     * хотим избежать (шаг 307).
     */
    public long insert(RequestLogEntity entity) {
        Long id = jdbc.queryForObject(INSERT, params(entity), Long.class);
        return Optional.ofNullable(id)
                .orElseThrow(() -> new IllegalStateException("request_log не вернул id: БД приняла вставку без RETURNING"));
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

    /**
     * Удаляет не более {@code limit} записей старше {@code before} одним
     * оператором.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} в подзапросе (ADR-043): строки,
     * которые уже забрал другой сборщик мусора, пропускаются вместо
     * ожидания блокировки - пользовательский запрос никогда не встаёт в
     * очередь за мусор (шаги 349-351). Порция ограничена, потому что
     * неограниченный DELETE по журналу держит транзакцию открытой и
     * раздувает WAL.
     */
    public int deleteOlderThan(Instant before, int limit) {
        String sql = """
            DELETE FROM storm.request_log
            WHERE (id, created_at) IN (
                SELECT id, created_at FROM storm.request_log
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
