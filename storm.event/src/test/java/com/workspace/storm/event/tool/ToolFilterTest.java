package com.workspace.storm.event.tool;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.offer.Price;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class ToolFilterTest {

    private Offer offer(String id, String byn) {
        Offer o = new Offer();
        o.setId(id);
        o.setPrice(Price.ofByn(new BigDecimal(byn)));
        return o;
    }

    @Test
    void sortsByPriceAndAppliesBudget() {
        List<Offer> out = ToolFilter.apply(
                List.of(offer("a", "50.00"), offer("b", "20.00"), offer("c", "200.00")),
                false, new BigDecimal("100"), null);
        assertEquals(List.of("b", "a"), out.stream().map(Offer::getId).toList());
    }

    @Test
    void topNLimits() {
        List<Offer> out = ToolFilter.apply(
                List.of(offer("a", "10"), offer("b", "20"), offer("c", "30")),
                false, null, 2);
        assertEquals(2, out.size());
    }
}
