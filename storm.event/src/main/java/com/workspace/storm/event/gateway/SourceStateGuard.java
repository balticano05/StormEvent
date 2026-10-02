package com.workspace.storm.event.gateway;

import com.workspace.storm.event.db.entity.SourceStateEntity;
import com.workspace.storm.event.db.repository.SourceStateRepository;

import java.util.Optional;

/** Читает флаг enabled/draining перед каждым вызовом (ADR-VL-04, шаг 536). */
public final class SourceStateGuard {

    private final SourceStateRepository repository;

    public SourceStateGuard(SourceStateRepository repository) {
        this.repository = repository;
    }

    public boolean isCallable(String sourceId) {
        if (repository == null) {
            return true;
        }
        Optional<SourceStateEntity> state = repository.findBySource(sourceId);
        return state.map(s -> s.isEnabled() && !s.isDraining()).orElse(true);
    }
}
