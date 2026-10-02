package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.offer.Price;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

class RankerTest {

    private Offer offer(String id, String byn, long durationMinutes) {
        Offer o = new Offer();
        o.setId(id);
        o.setPrice(Price.ofByn(new BigDecimal(byn)));
        o.setDepartureAt(Instant.parse("2026-10-05T10:00:00Z"));
        o.setArrivalAt(Instant.parse("2026-10-05T10:00:00Z").plusSeconds(durationMinutes * 60));
        return o;
    }

    @Test
    void cheapestSortsByAmountByn() {
        List<Offer> out = Ranker.topN(List.of(
                offer("a", "30", 60), offer("b", "10", 120), offer("c", "20", 90)),
                Ranker.Mode.CHEAPEST, 10);
        assertEquals(List.of("b", "c", "a"), out.stream().map(Offer::getId).toList());
    }

    @Test
    void fastestSortsByDuration() {
        List<Offer> out = Ranker.topN(List.of(
                offer("a", "30", 120), offer("b", "10", 60), offer("c", "20", 90)),
                Ranker.Mode.FASTEST, 10);
        assertEquals(List.of("b", "c", "a"), out.stream().map(Offer::getId).toList());
    }

    @Test
    void topNLimits() {
        List<Offer> out = Ranker.topN(List.of(
                offer("a", "30", 120), offer("b", "10", 60), offer("c", "20", 90)),
                Ranker.Mode.CHEAPEST, 2);
        assertEquals(2, out.size());
    }
}
