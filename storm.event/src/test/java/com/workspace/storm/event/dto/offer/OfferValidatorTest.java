package com.workspace.storm.event.dto.offer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OfferValidatorTest {

    private Offer offer(Instant departure, Instant arrival, Integer seats) {
        Offer o = new Offer("1", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                departure, arrival, Price.ofByn(new BigDecimal("25.00")), seats, "link", Map.of());
        return o;
    }

    @Test
    void dropsPastDeparture() {
        assertEquals("past_departure", OfferValidator.dropReason(
                offer(Instant.now().minusSeconds(3600), null, 5)));
    }

    @Test
    void dropsSoldOut() {
        assertEquals("sold_out", OfferValidator.dropReason(
                offer(Instant.now().plusSeconds(3600), null, 0)));
    }

    @Test
    void dropsArrivalBeforeDeparture() {
        assertEquals("arrival_before_departure", OfferValidator.dropReason(
                offer(Instant.now().plusSeconds(7200), Instant.now().plusSeconds(3600), 5)));
    }

    @Test
    void acceptsValid() {
        assertTrue(OfferValidator.isValid(
                offer(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 5)));
    }

    @Test
    void dropsNull() {
        assertEquals("null_offer", OfferValidator.dropReason(null));
    }
}
