package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.mapper.CachedResultRowMapper;
import com.workspace.storm.event.db.support.SqlParams;
import lombok.RequiredArgsConstructor;
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
        return jdbc.query(sql, SqlParams.create().add("cacheKey", cacheKey).build(), rowMapper)
                .stream().findFirst();
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
        jdbc.update(sql, SqlParams.create()
                .add("cacheKey", entity.getCacheKey())
                .add("source", entity.getSource())
                .add("domain", entity.getDomain())
                .add("payloadJson", entity.getPayloadJson())
                .addInstant("createdAt", entity.getCreatedAt())
                .addInstant("expiresAt", entity.getExpiresAt())
                .add("stale", entity.isStale())
                .build());
    }

    public List<CachedResultEntity> findExpired(Instant before) {
        String sql = "SELECT * FROM storm.cached_result WHERE expires_at < :before";
        return jdbc.query(sql, SqlParams.create().addInstant("before", before).build(), rowMapper);
    }

    public List<String> findExpiredKeys(Instant before, int limit) {
        String sql = """
            SELECT cache_key FROM storm.cached_result
            WHERE expires_at < :before
            ORDER BY expires_at
            LIMIT :limit
            """;
        return jdbc.queryForList(sql, SqlParams.create()
                .addInstant("before", before)
                .add("limit", limit)
                .build(), String.class);
    }

    public int deleteByKeys(List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM storm.cached_result WHERE cache_key IN (:keys)";
        return jdbc.update(sql, SqlParams.create().add("keys", keys).build());
    }

    public void delete(String cacheKey) {
        String sql = "DELETE FROM storm.cached_result WHERE cache_key = :cacheKey";
        jdbc.update(sql, SqlParams.create().add("cacheKey", cacheKey).build());
    }

    public void markStale(String cacheKey) {
        String sql = "UPDATE storm.cached_result SET stale = TRUE WHERE cache_key = :cacheKey";
        jdbc.update(sql, SqlParams.create().add("cacheKey", cacheKey).build());
    }
}
