package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SourceStateEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class SourceStateRepositoryTest extends PostgresTestSupport {

    private static final int SEEDED_SOURCES = 5;

    private SourceStateEntity state(String source, boolean enabled, boolean draining) {
        SourceStateEntity entity = new SourceStateEntity();
        entity.setSource(source);
        entity.setEnabled(enabled);
        entity.setDraining(draining);
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Test
    void seedCreatesFiveEnabledSources() {
        assertEquals(SEEDED_SOURCES, sourceStateRepository.findAll().size());
        assertEquals(SEEDED_SOURCES, sourceStateRepository.findByEnabled(true).size());
    }

    @Test
    void upsertInsertsUnknownSource() {
        SourceStateEntity entity = state("custom", true, false);

        sourceStateRepository.upsert(entity);

        Optional<SourceStateEntity> stored = sourceStateRepository.findBySource("custom");
        assertTrue(stored.isPresent());
        assertTrue(stored.orElseThrow().isEnabled());
    }

    @Test
    void upsertUpdatesExistingSourceWithoutDuplicating() {
        SourceStateEntity updated = state("bzd", false, true);

        sourceStateRepository.upsert(updated);

        assertEquals(SEEDED_SOURCES, sourceStateRepository.findAll().size());
        SourceStateEntity stored = sourceStateRepository.findBySource("bzd").orElseThrow();
        assertFalse(stored.isEnabled());
        assertTrue(stored.isDraining());
    }

    @Test
    void enabledFilterReflectsLatestUpsert() {
        sourceStateRepository.upsert(state("ticketbus", false, false));

        assertFalse(sourceStateRepository.findByEnabled(true).stream()
                .anyMatch(entity -> entity.getSource().equals("ticketbus")));
        assertEquals(SEEDED_SOURCES - 1, sourceStateRepository.findByEnabled(true).size());
    }

    @Test
    void rampUntilSurvivesRoundTrip() {
        Instant rampUntil = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
        SourceStateEntity entity = state("belhotel", true, true);
        entity.setRampUntil(rampUntil);

        sourceStateRepository.upsert(entity);

        assertEquals(rampUntil, sourceStateRepository.findBySource("belhotel").orElseThrow().getRampUntil());
    }

    @Test
    void nullableRampUntilStaysNull() {
        sourceStateRepository.upsert(state("bzd", true, false));

        assertNull(sourceStateRepository.findBySource("bzd").orElseThrow().getRampUntil());
    }

    @Test
    void seedIsIdempotent() {
        sourceStateRepository.upsert(state("bzd", true, false));
        sourceStateRepository.upsert(state("bzd", true, false));

        List<SourceStateEntity> all = sourceStateRepository.findAll();
        assertEquals(SEEDED_SOURCES, all.size());
    }

    @Test
    void unknownSourceIsEmpty() {
        assertTrue(sourceStateRepository.findBySource("nope").isEmpty());
    }
}
