package com.workspace.storm.event.normalize.source;

import com.workspace.storm.event.dto.offer.EventOffer;
import com.workspace.storm.event.dto.offer.HotelOffer;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.dto.offer.TransportOffer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MappersAggregationTest {

    @Test
    void emptyInputsYieldEmptyLists() {
        assertTrue(AtlasOfferMapper.map(null).isEmpty());
        assertTrue(BzdOfferMapper.map(null).isEmpty());
        assertTrue(TicketBusOfferMapper.map((List) null).isEmpty());
        assertTrue(TicketProOfferMapper.map(null).isEmpty());
        assertTrue(BelHotelOfferMapper.map(null).isEmpty());
    }

    @Test
    void nullRidesYieldNulls() {
        assertNull(AtlasOfferMapper.mapRide(null));
        assertNull(BzdOfferMapper.mapTrain(null));
        assertNull(TicketBusOfferMapper.mapRace(null));
        assertNull(TicketProOfferMapper.mapEvent(null));
        assertNull(BelHotelOfferMapper.mapRoomOffer(null, null));
    }

    @Test
    void offerSurvivesJsonRoundTripWithoutPrecisionLoss() throws Exception {
        Offer offer = new TransportOffer();
        offer.setId("atlas:transport:1");
        offer.setDomain(OfferDomain.TRANSPORT);
        offer.setSource("atlas");
        offer.setKind("bus");
        offer.setFrom("Минск");
        offer.setTo("Гродно");
        offer.setDepartureAt(Instant.parse("2026-10-05T08:00:00Z"));
        offer.setArrivalAt(Instant.parse("2026-10-05T13:00:00Z"));
        offer.setPrice(Price.ofByn(new BigDecimal("25.50")));
        offer.setSeats(12);
        offer.setLink("https://example.com");
        offer.setAttributes(Map.of("partial", false));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(offer);
        Offer restored = mapper.readValue(json, TransportOffer.class);

        assertEquals(offer.getId(), restored.getId());
        assertEquals(0, offer.getPrice().getAmount().compareTo(restored.getPrice().getAmount()));
        assertEquals(offer.getSeats(), restored.getSeats());
        assertEquals(offer.getDepartureAt(), restored.getDepartureAt());
    }
}
