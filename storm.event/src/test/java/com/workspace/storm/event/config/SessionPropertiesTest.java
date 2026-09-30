package com.workspace.storm.event.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionPropertiesTest {

    @Test
    void sessionTtlIs15MinutesByDefault() {
        assertEquals(15, new SessionProperties().getTtlMinutes());
        assertEquals(900, new SessionProperties().ttlSeconds());
    }

    @Test
    void idempotencyTtlIs5MinutesByDefault() {
        assertEquals(5, new IdempotencyProperties().getTtlMinutes());
        assertEquals(300, new IdempotencyProperties().ttlSeconds());
    }

    @Test
    void ttlIsDerivedFromConfiguredMinutes() {
        SessionProperties session = new SessionProperties();
        session.setTtlMinutes(30);
        IdempotencyProperties idempotency = new IdempotencyProperties();
        idempotency.setTtlMinutes(2);

        assertEquals(1800, session.ttlSeconds());
        assertEquals(120, idempotency.ttlSeconds());
    }
}
