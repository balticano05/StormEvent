package com.workspace.storm.event.cache;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.repository.CacheRepository;

import java.time.Instant;
import java.util.Optional;

/** Управление кэшем поверх CacheRepository: fresh приоритетнее stale (ADR-042). */
public class CacheManager {

    private final CacheRepository repository;

    public CacheManager(CacheRepository repository) {
        this.repository = repository;
    }

    public Optional<CachedResultEntity> getFresh(String key) {
        return repository.getFresh(key, Instant.now());
    }

    public Optional<CachedResultEntity> getStale(String key) {
        return repository.get(key).filter(CachedResultEntity::isStale);
    }

    public void put(CachedResultEntity entity) {
        repository.put(entity);
    }

    public void markStale(String key) {
        repository.markStale(key);
    }
}
