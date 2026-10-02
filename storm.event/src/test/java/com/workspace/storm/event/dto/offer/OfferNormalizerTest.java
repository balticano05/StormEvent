package com.workspace.storm.event.dto.offer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OfferNormalizerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    @ParameterizedTest
    @MethodSource("priceProvider")
    void normalizesPrice(String input, String source, BigDecimal expectedAmount, String expectedCurrency, BigDecimal expectedByn) {
        Price price = OfferNormalizer.normalizePrice(input, source);
        assertNotNull(price, "Failed for: " + input);
        assertEquals(0, expectedAmount.compareTo(price.getAmount()));
        assertEquals(expectedCurrency, price.getCurrency().getCurrencyCode());
        assertEquals(0, expectedByn.compareTo(price.getAmountByn()));
    }

    static Stream<Arguments> priceProvider() {
        return Stream.of(
                Arguments.of("25.50 BYN", "atlas", new BigDecimal("25.50"), "BYN", new BigDecimal("25.50")),
                Arguments.of("1000 RUB", "ticketbus", new BigDecimal("1000"), "RUB", new BigDecimal("32.00")),
                Arguments.of("10 USD", "ticketpro", new BigDecimal("10"), "USD", new BigDecimal("32.50")),
                Arguments.of("5 EUR", "belhotel", new BigDecimal("5"), "EUR", new BigDecimal("17.50")),
                Arguments.of("25,50", "atlas", new BigDecimal("25.50"), "BYN", new BigDecimal("25.50")),
                Arguments.of("от 1000", "ticketbus", new BigDecimal("1000"), "BYN", new BigDecimal("1000.00")),
                Arguments.of("5000", "bzd", new BigDecimal("50.00"), "BYN", new BigDecimal("50.00"))
        );
    }

    @Test
    void returnsNullForInvalidPrice() {
        assertNull(OfferNormalizer.normalizePrice(null, "atlas"));
        assertNull(OfferNormalizer.normalizePrice("", "atlas"));
        assertNull(OfferNormalizer.normalizePrice("   ", "atlas"));
        assertNull(OfferNormalizer.normalizePrice("abc", "atlas"));
    }

    @Test
    void normalizesDateTime() {
        Instant result = OfferNormalizer.normalizeDateTime("02.10.2026", "15:30");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2026, 10, 2, 15, 30).atZone(ZONE).toInstant(), result);
    }

    @Test
    void normalizesDateOnly() {
        Instant result = OfferNormalizer.normalizeDateTime("02.10.2026");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2026, 10, 2, 0, 0).atZone(ZONE).toInstant(), result);
    }

    @Test
    void normalizesTimeOnlyUsesToday() {
        Instant result = OfferNormalizer.normalizeDateTime(null, "15:30");
        assertNotNull(result);
        assertEquals(LocalDateTime.now(ZONE).withHour(15).withMinute(30).atZone(ZONE).toLocalDate(), result.atZone(ZONE).toLocalDate());
    }

    @Test
    void normalizesIsoDateTime() {
        Instant result = OfferNormalizer.normalizeDateTime("2026-10-02T15:30:00");
        assertNotNull(result);
        assertEquals(Instant.parse("2026-10-02T12:30:00Z"), result);
    }

    @Test
    void normalizeCity() {
        assertEquals("Минск", OfferNormalizer.normalizeCity("Мінск"));
        assertEquals("Минск", OfferNormalizer.normalizeCity("Минск"));
        assertEquals("Гродно", OfferNormalizer.normalizeCity("  Гродно  "));
        assertNull(OfferNormalizer.normalizeCity(null));
    }

    @Test
    void normalizeKey() {
        assertEquals("minsk", OfferNormalizer.normalizeKey("Минск"));
        assertEquals("minsk", OfferNormalizer.normalizeKey("Мінск"));
        assertEquals("minsk", OfferNormalizer.normalizeKey("Minsk"));
        assertEquals("minskgrodno", OfferNormalizer.normalizeKey("Минск-Гродно"));
        assertEquals("", OfferNormalizer.normalizeKey(null));
    }

    @Test
    void validatesRequiredFields() {
        Offer valid = new Offer("1", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                Price.ofByn(new BigDecimal("25.00")), 10, "link", Map.of());
        assertTrue(OfferNormalizer.isRequiredFieldPresent(valid));

        Offer noFrom = new Offer("2", OfferDomain.TRANSPORT, "atlas", "bus", "", "Гродно",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                Price.ofByn(new BigDecimal("25.00")), 10, "link", Map.of());
        assertFalse(OfferNormalizer.isRequiredFieldPresent(noFrom));

        Offer noPrice = new Offer("3", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                null, 10, "link", Map.of());
        assertFalse(OfferNormalizer.isRequiredFieldPresent(noPrice));

        Offer zeroPrice = new Offer("4", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                Price.ofByn(BigDecimal.ZERO), 10, "link", Map.of());
        assertFalse(OfferNormalizer.isRequiredFieldPresent(zeroPrice));
    }

    @Test
    void validateAndGetDropReason() {
        Offer valid = new Offer("1", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                Price.ofByn(new BigDecimal("25.00")), 10, "link", Map.of());
        assertTrue(OfferNormalizer.validateAndGetDropReason(valid).isEmpty());

        Offer past = new Offer("2", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(7200),
                Price.ofByn(new BigDecimal("25.00")), 10, "link", Map.of());
        assertEquals("past_departure", OfferNormalizer.validateAndGetDropReason(past).orElse(null));

        Offer negativeSeats = new Offer("3", OfferDomain.TRANSPORT, "atlas", "bus", "Минск", "Гродно",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                Price.ofByn(new BigDecimal("25.00")), -1, "link", Map.of());
        assertEquals("invalid_seats", OfferNormalizer.validateAndGetDropReason(negativeSeats).orElse(null));
    }
}