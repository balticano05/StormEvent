package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.mapper.SessionRowMapper;
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
public class SessionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionRowMapper rowMapper = new SessionRowMapper();

    public void insert(SessionEntity entity) {
        String sql = """
            INSERT INTO storm.session (id, intent, last_access_at, created_at, ttl_seconds, state)
            VALUES (:id, :intent::jsonb, :lastAccessAt, :createdAt, :ttlSeconds, :state)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entity.getId())
                .addValue("intent", entity.getIntent())
                .addValue("lastAccessAt", entity.getLastAccessAt())
                .addValue("createdAt", entity.getCreatedAt())
                .addValue("ttlSeconds", entity.getTtlSeconds())
                .addValue("state", entity.getState());
        jdbc.update(sql, params);
    }

    public Optional<SessionEntity> findById(UUID id) {
        String sql = "SELECT * FROM storm.session WHERE id = :id";
        List<SessionEntity> result = jdbc.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return result.stream().findFirst();
    }

    public void updateIntent(UUID id, String intent) {
        String sql = "UPDATE storm.session SET intent = :intent::jsonb WHERE id = :id";
        jdbc.update(sql, new MapSqlParameterSource().addValue("id", id).addValue("intent", intent));
    }

    public void touch(UUID id) {
        String sql = "UPDATE storm.session SET last_access_at = NOW() WHERE id = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    public void updateState(UUID id, String state) {
        String sql = "UPDATE storm.session SET state = :state WHERE id = :id";
        jdbc.update(sql, new MapSqlParameterSource().addValue("id", id).addValue("state", state));
    }

    public void delete(UUID id) {
        String sql = "DELETE FROM storm.session WHERE id = :id";
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    public List<SessionEntity> findExpired(Instant before) {
        String sql = "SELECT * FROM storm.session WHERE created_at + (ttl_seconds || ' seconds')::interval < :before";
        return jdbc.query(sql, new MapSqlParameterSource("before", before), rowMapper);
    }

    public List<SessionEntity> findAllByState(String state) {
        String sql = "SELECT * FROM storm.session WHERE state = :state";
        return jdbc.query(sql, new MapSqlParameterSource("state", state), rowMapper);
    }
}