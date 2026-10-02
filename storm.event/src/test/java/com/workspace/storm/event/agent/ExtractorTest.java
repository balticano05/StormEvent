package com.workspace.storm.event.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExtractorTest {

    @Test
    void detectsBusAndTrain() {
        SearchIntent intent = new Extractor().extract("автобус и поезд в Минск");
        assertTrue(intent.domains().contains("BUS"));
        assertTrue(intent.domains().contains("TRAIN"));
    }

    @Test
    void defaultsToBus() {
        SearchIntent intent = new Extractor().extract("хочу куда-нибудь");
        assertEquals(1, intent.domains().size());
        assertEquals("BUS", intent.domains().get(0));
    }

    @Test
    void detectsHotel() {
        SearchIntent intent = new Extractor().extract("нужен отель");
        assertTrue(intent.domains().contains("HOTEL"));
    }
}
