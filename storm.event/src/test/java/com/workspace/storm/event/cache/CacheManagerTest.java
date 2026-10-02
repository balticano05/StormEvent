package com.workspace.storm.event.cache;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.repository.CacheRepository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

class CacheManagerTest {

    @Test
    void prefersFreshOverStale() {
        CachedResultEntity stale = new CachedResultEntity();
        stale.setCacheKey("k");
        stale.setStale(true);
        stale.setExpiresAt(Instant.now().minusSeconds(60));
        CacheRepository repository = new CacheRepository(null) {
            @Override public Optional<CachedResultEntity> getFresh(String cacheKey, Instant now) { return Optional.empty(); }
            @Override public Optional<CachedResultEntity> get(String cacheKey) { return Optional.of(stale); }
        };
        CacheManager manager = new CacheManager(repository);
        assertTrue(manager.getFresh("k").isEmpty());
        assertTrue(manager.getStale("k").isPresent());
    }
}
