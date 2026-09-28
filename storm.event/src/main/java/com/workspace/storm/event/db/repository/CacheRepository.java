package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.mapper.CachedResultRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CacheRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final CachedResultRowMapper rowMapper = new CachedResultRowMapper();

    public Optional<CachedResultEntity> get(String cacheKey) {
        String sql = "SELECT * FROM storm.cached_result WHERE cache_key = :cacheKey";
        List<CachedResultEntity> result = jdbc.query(sql, new MapSqlParameterSource("cacheKey", cacheKey), rowMapper);
        return result.stream().findFirst();
    }

    public void put(CachedResultEntity entity) {
        String sql = """
            INSERT INTO storm.cached_result (cache_key, source, domain, payload_json, created_at, expires_at, stale)
            VALUES (:cacheKey, :source, :domain, :payloadJson::jsonb, :createdAt, :expiresAt, :stale)
            ON CONFLICT (cache_key) DO UPDATE SET
                payload_json = EXCLUDED.payload_json,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at,
                stale = EXCLUDED.stale
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cacheKey", entity.getCacheKey())
                .addValue("source", entity.getSource())
                .addValue("domain", entity.getDomain())
                .addValue("payloadJson", entity.getPayloadJson())
                .addValue("createdAt", entity.getCreatedAt())
                .addValue("expiresAt", entity.getExpiresAt())
                .addValue("stale", entity.isStale());
        jdbc.update(sql, params);
    }

    public List<CachedResultEntity> findExpired(Instant before) {
        String sql = "SELECT * FROM storm.cached_result WHERE expires_at < :before";
        return jdbc.query(sql, new MapSqlParameterSource("before", before), rowMapper);
    }

    public void delete(String cacheKey) {
        String sql = "DELETE FROM storm.cached_result WHERE cache_key = :cacheKey";
        jdbc.update(sql, new MapSqlParameterSource("cacheKey", cacheKey));
    }

    public void markStale(String cacheKey) {
        String sql = "UPDATE storm.cached_result SET stale = TRUE WHERE cache_key = :cacheKey";
        jdbc.update(sql, new MapSqlParameterSource("cacheKey", cacheKey));
    }
}