package com.workspace.storm.event.dto.offer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfferIdFactoryTest {

    @Test
    void createTransportId() {
        String id = OfferIdFactory.createTransport("atlas", "ride-123");
        assertEquals("atlas:transport:ride-123", id);
    }

    @Test
    void createEventId() {
        String id = OfferIdFactory.createEvent("ticketpro", "event-456");
        assertEquals("ticketpro:event:event-456", id);
    }

    @Test
    void createHotelId() {
        String id = OfferIdFactory.createHotel("belhotel", "room-789");
        assertEquals("belhotel:hotel:room-789", id);
    }

    @Test
    void idsAreUniqueAcrossSources() {
        String atlasId = OfferIdFactory.createTransport("atlas", "123");
        String ticketbusId = OfferIdFactory.createTransport("ticketbus", "123");
        assertNotEquals(atlasId, ticketbusId);
    }

    @Test
    void idsAreStableForSameInput() {
        String id1 = OfferIdFactory.createTransport("atlas", "ride-123");
        String id2 = OfferIdFactory.createTransport("atlas", "ride-123");
        assertEquals(id1, id2);
    }

    @Test
    void genericCreate() {
        String id = OfferIdFactory.create("bzd", "train", "610B");
        assertEquals("bzd:train:610B", id);
    }
}