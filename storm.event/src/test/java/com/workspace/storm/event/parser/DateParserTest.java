package com.workspace.storm.event.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DateParserTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    @ParameterizedTest
    @MethodSource("isoDateTimeProvider")
    void parsesIsoDateTime(String input, Instant expected) {
        Instant result = DateParser.parse(input, ZONE);
        assertEquals(expected, result, "Failed for: " + input);
    }

    static Stream<Arguments> isoDateTimeProvider() {
        return Stream.of(
                Arguments.of("2026-10-02T15:30:00", Instant.parse("2026-10-02T12:30:00Z")),
                Arguments.of("2026-10-02T15:30:00Z", Instant.parse("2026-10-02T15:30:00Z"))
        );
    }

    @ParameterizedTest
    @MethodSource("dateTimeProvider")
    void parsesDateTime(String input, Instant expected) {
        Instant result = DateParser.parse(input, ZONE);
        assertEquals(expected, result, "Failed for: " + input);
    }

    static Stream<Arguments> dateTimeProvider() {
        return Stream.of(
                Arguments.of("02.10.2026 15:30", LocalDateTime.of(2026, 10, 2, 15, 30).atZone(ZONE).toInstant()),
                Arguments.of("2.10.2026 9:05", LocalDateTime.of(2026, 10, 2, 9, 5).atZone(ZONE).toInstant()),
                Arguments.of("02/10/2026 15:30", LocalDateTime.of(2026, 10, 2, 15, 30).atZone(ZONE).toInstant())
        );
    }

    @ParameterizedTest
    @MethodSource("dateOnlyProvider")
    void parsesDateOnly(String input) {
        Instant result = DateParser.parse(input, ZONE);
        assertNotNull(result);
        LocalDate expected = LocalDate.of(2026, 10, 2);
        assertEquals(expected, result.atZone(ZONE).toLocalDate());
    }

    static Stream<Arguments> dateOnlyProvider() {
        return Stream.of(
                Arguments.of("02.10.2026"),
                Arguments.of("2.10.2026"),
                Arguments.of("02/10/2026")
        );
    }

    @ParameterizedTest
    @MethodSource("timeOnlyProvider")
    void parsesTimeOnly(String input) {
        Instant result = DateParser.parse(input, ZONE);
        assertNotNull(result);
        LocalDate today = LocalDate.now(ZONE);
        assertEquals(today, result.atZone(ZONE).toLocalDate());
    }

    static Stream<Arguments> timeOnlyProvider() {
        return Stream.of(
                Arguments.of("15:30"),
                Arguments.of("9:05"),
                Arguments.of("15.30")
        );
    }

    @Test
    void parsesToday() {
        Instant result = DateParser.parse("сегодня", ZONE);
        assertNotNull(result);
        assertEquals(LocalDate.now(ZONE), result.atZone(ZONE).toLocalDate());
    }

    @Test
    void parsesTomorrow() {
        Instant result = DateParser.parse("завтра", ZONE);
        assertNotNull(result);
        assertEquals(LocalDate.now(ZONE).plusDays(1), result.atZone(ZONE).toLocalDate());
    }

    @Test
    void returnsNullForBlank() {
        assertNull(DateParser.parse("", ZONE));
        assertNull(DateParser.parse("   ", ZONE));
        assertNull(DateParser.parse(null, ZONE));
    }

    @Test
    void parseWithDefaultTimeCombinesDateAndTime() {
        Instant result = DateParser.parseWithDefaultTime("02.10.2026", "15:30", ZONE);
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2026, 10, 2, 15, 30).atZone(ZONE).toInstant(), result);
    }

    @Test
    void parseWithDefaultTimeUsesDateStartWhenTimeNull() {
        Instant date = DateParser.parse("02.10.2026", ZONE);
        Instant result = DateParser.parseWithDefaultTime("02.10.2026", null, ZONE);
        assertEquals(date, result);
    }
}