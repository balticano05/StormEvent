package com.workspace.storm.event.dto.offer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfferMetricsTest {

    private final OfferMetrics metrics = new OfferMetrics();

    @BeforeEach
    void reset() {
        metrics.clear();
    }

    @Test
    void countsNormalizedBySource() {
        metrics.recordNormalized("bzd");
        metrics.recordNormalized("bzd");
        metrics.recordNormalized("atlas");
        assertEquals(2, metrics.normalizedCount("bzd"));
        assertEquals(1, metrics.normalizedCount("atlas"));
        assertEquals(0, metrics.normalizedCount("ticketbus"));
    }

    @Test
    void countsDroppedByReason() {
        metrics.recordDropped("past_departure");
        metrics.recordDropped("past_departure");
        metrics.recordDropped("sold_out");
        assertEquals(2, metrics.droppedCount("past_departure"));
        assertEquals(1, metrics.droppedCount("sold_out"));
    }
}
