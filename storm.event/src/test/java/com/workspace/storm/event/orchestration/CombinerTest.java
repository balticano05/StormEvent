package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.dto.source.ToolResult;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class CombinerTest {

    private Offer offer(String id, BigDecimal price) {
        Offer o = new Offer();
        o.setId(id);
        o.setDomain(OfferDomain.TRANSPORT);
        o.setPrice(Price.ofByn(price));
        return o;
    }

    @Test
    void mergeDedupesById() {
        List<ToolResult> results = List.of(
                new ToolResult("BUS", List.of(offer("a", new BigDecimal("10")), offer("b", new BigDecimal("20"))), List.of(), List.of(), 1L),
                new ToolResult("TRAIN", List.of(offer("a", new BigDecimal("10")), offer("c", new BigDecimal("30"))), List.of(), List.of(), 1L));
        assertEquals(3, Combiner.merge(results).size());
    }

    @Test
    void emptyStaysEmpty() {
        assertTrue(Combiner.merge(List.of()).isEmpty());
        assertTrue(Combiner.merge(List.of(ToolResult.empty("BUS"))).isEmpty());
    }

    @Test
    void groupsByDomain() {
        List<ToolResult> results = List.of(
                new ToolResult("BUS", List.of(offer("a", new BigDecimal("10"))), List.of(), List.of(), 1L),
                new ToolResult("TRAIN", List.of(offer("b", new BigDecimal("20"))), List.of(), List.of(), 1L));
        Map<String, List<Offer>> grouped = Combiner.groupByDomain(results);
        assertEquals(2, grouped.size());
        assertEquals(1, grouped.get("BUS").size());
    }
}
