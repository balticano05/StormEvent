package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.mapper.SessionRowMapper;
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
public class SessionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionRowMapper rowMapper = new SessionRowMapper();

    public void insert(SessionEntity entity) {
        String sql = """
            INSERT INTO storm.session (id, intent, last_access_at, expires_at, created_at, ttl_seconds, state)
            VALUES (:id, :intent::jsonb, :lastAccessAt, :expiresAt, :createdAt, :ttlSeconds, :state)
            """;
        jdbc.update(sql, SqlParams.create()
                .add("id", entity.getId())
                .add("intent", entity.getIntent())
                .addInstant("lastAccessAt", entity.getLastAccessAt())
                .addInstant("expiresAt", entity.getExpiresAt())
                .addInstant("createdAt", entity.getCreatedAt())
                .add("ttlSeconds", entity.getTtlSeconds())
                .add("state", entity.getState())
                .build());
    }

    public Optional<SessionEntity> findById(UUID id) {
        String sql = "SELECT * FROM storm.session WHERE id = :id";
        List<SessionEntity> result = jdbc.query(sql, SqlParams.create().add("id", id).build(), rowMapper);
        return result.stream().findFirst();
    }

    public void updateIntent(UUID id, String intent) {
        String sql = "UPDATE storm.session SET intent = :intent::jsonb WHERE id = :id";
        jdbc.update(sql, SqlParams.create().add("id", id).add("intent", intent).build());
    }

    public void touch(UUID id) {
        String sql = """
            UPDATE storm.session
            SET last_access_at = NOW(),
                expires_at = NOW() + ttl_seconds * INTERVAL '1 second'
            WHERE id = :id
            """;
        jdbc.update(sql, SqlParams.create().add("id", id).build());
    }

    public void updateState(UUID id, String state) {
        String sql = "UPDATE storm.session SET state = :state WHERE id = :id";
        jdbc.update(sql, SqlParams.create().add("id", id).add("state", state).build());
    }

    public void delete(UUID id) {
        String sql = "DELETE FROM storm.session WHERE id = :id";
        jdbc.update(sql, SqlParams.create().add("id", id).build());
    }

    public List<UUID> findExpiredIds(Instant before, int limit) {
        String sql = """
            SELECT id FROM storm.session
            WHERE expires_at < :before
            ORDER BY expires_at
            LIMIT :limit
            """;
        return jdbc.queryForList(sql, SqlParams.create()
                .addInstant("before", before)
                .add("limit", limit)
                .build(), UUID.class);
    }

    public int deleteByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM storm.session WHERE id IN (:ids)";
        return jdbc.update(sql, SqlParams.create().add("ids", ids).build());
    }

    public List<SessionEntity> findAllByState(String state) {
        String sql = "SELECT * FROM storm.session WHERE state = :state";
        return jdbc.query(sql, SqlParams.create().add("state", state).build(), rowMapper);
    }
}
