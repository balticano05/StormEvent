package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SourceStateEntity;
import com.workspace.storm.event.db.mapper.SourceStateRowMapper;
import com.workspace.storm.event.db.support.SqlParams;
import lombok.RequiredArgsConstructor;
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
        jdbc.update(sql, SqlParams.create()
                .add("source", entity.getSource())
                .add("enabled", entity.isEnabled())
                .add("draining", entity.isDraining())
                .addInstant("rampUntil", entity.getRampUntil())
                .addInstant("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now())
                .build());
    }

    public Optional<SourceStateEntity> findBySource(String source) {
        String sql = "SELECT * FROM storm.source_state WHERE source = :source";
        return jdbc.query(sql, SqlParams.create().add("source", source).build(), rowMapper)
                .stream().findFirst();
    }

    public List<SourceStateEntity> findByEnabled(boolean enabled) {
        String sql = "SELECT * FROM storm.source_state WHERE enabled = :enabled";
        return jdbc.query(sql, SqlParams.create().add("enabled", enabled).build(), rowMapper);
    }

    public List<SourceStateEntity> findAll() {
        return jdbc.query("SELECT * FROM storm.source_state", rowMapper);
    }
}
