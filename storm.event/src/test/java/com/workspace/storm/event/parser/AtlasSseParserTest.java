package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.atlas.AtlasRide;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasSseParserTest {

    private final AtlasSseParser parser = new AtlasSseParser();

    @Test
    void parsesFullSseStream() {
        String sse = """
                event: progress
                data: {"type":"progress","completed":1,"total":2}

                event: rides
                data: {"rides":[{"id":"ims4:r1","rideId":"ims4:r1","name":"Гродно Барановичи ","carrier":"ЧП «Бусонлайн»","currency":"BYN","price":30,"departure":"2026-09-24T10:00:00","arrival":"2026-09-24T13:00:00","freeSeats":15,"status":"sale","from":{"id":"c627904","desc":"Гродно"},"to":{"id":"c630429","desc":"Барановичи"},"pickupStops":[{"id":"s1","desc":"Автовокзал","city":"Гродно"}],"bus":{"mark":"Renault","model":"MASTER","reg":"1 TAX 7159"}}]}

                event: progress
                data: {"type":"progress","completed":2,"total":2}

                event: done
                data: {"type":"done","partial":false,"totalRides":1,"durationMs":196,"succeeded":["transit","ims4"],"failed":[]}
                """;

        AtlasSearchResult result = parser.parse(sse);

        assertNotNull(result);
        assertTrue(result.isProgressReached());
        assertEquals(2, result.getProgressCompleted());
        assertEquals(2, result.getProgressTotal());
        assertEquals(1, result.getRides().size());
        assertEquals(1, result.getTotalRides());
        assertFalse(result.isPartial());
        assertEquals(196, result.getDurationMs());
        assertTrue(result.getSucceeded().contains("ims4"));
        assertTrue(result.getFailed().isEmpty());
        assertTrue(result.getErrors().isEmpty());

        AtlasRide ride = result.getRides().get(0);
        assertEquals("ims4:r1", ride.getId());
        assertEquals("Гродно Барановичи ", ride.getName());
        assertEquals("ЧП «Бусонлайн»", ride.getCarrier());
        assertEquals(30, ride.getPrice().intValue());
    }

    @Test
    void recordsErrorsAndSkipsPartialRidePayloads() {
        String sse = """
                event: rides
                data: {"foo":"bar"}

                event: error
                data: {"code":"ECONNABORTED","message":"upstream timeout"}

                event: done
                data: {"type":"done","partial":true,"totalRides":0,"durationMs":10,"succeeded":[],"failed":["ims4"]}
                """;

        AtlasSearchResult result = parser.parse(sse);

        assertTrue(result.getRides().isEmpty());
        assertTrue(result.isPartial());
        assertTrue(result.getFailed().contains("ims4"));
        assertEquals(1, result.getErrors().size());
    }

    @Test
    void refusesBlankInput() {
        try {
            parser.parse("   \n  ");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

}