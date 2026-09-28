package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SourceStateEntity;
import com.workspace.storm.event.db.mapper.SourceStateRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SourceStateRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SourceStateRowMapper rowMapper = new SourceStateRowMapper();

    public void upsert(SourceStateEntity entity) {
        String sql = """
            INSERT INTO storm.source_state (source, enabled, draining, ramp_until, updated_at)
            VALUES (:source, :enabled, :draining, :rampUntil, :updatedAt)
            ON CONFLICT (source) DO UPDATE SET
                enabled = EXCLUDED.enabled,
                draining = EXCLUDED.draining,
                ramp_until = EXCLUDED.ramp_until,
                updated_at = EXCLUDED.updated_at
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("source", entity.getSource())
                .addValue("enabled", entity.isEnabled())
                .addValue("draining", entity.isDraining())
                .addValue("rampUntil", entity.getRampUntil())
                .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now());
        jdbc.update(sql, params);
    }

    public Optional<SourceStateEntity> findBySource(String source) {
        String sql = "SELECT * FROM storm.source_state WHERE source = :source";
        List<SourceStateEntity> result = jdbc.query(sql, new MapSqlParameterSource("source", source), rowMapper);
        return result.stream().findFirst();
    }

    public List<SourceStateEntity> findByEnabled(boolean enabled) {
        String sql = "SELECT * FROM storm.source_state WHERE enabled = :enabled";
        return jdbc.query(sql, new MapSqlParameterSource("enabled", enabled), rowMapper);
    }

    public List<SourceStateEntity> findAll() {
        return jdbc.query("SELECT * FROM storm.source_state", rowMapper);
    }
}