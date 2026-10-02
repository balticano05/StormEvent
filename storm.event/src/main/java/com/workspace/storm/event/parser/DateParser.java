package com.workspace.storm.event.parser;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateParser {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Minsk");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2})[:.](\\d{2})$");
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\s+(\\d{1,2})[:.](\\d{2})$");
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})[./](\\d{4})$");
    private static final Pattern ISO_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

    private DateParser() {
    }

    public static Instant parse(String input) {
        return parse(input, DEFAULT_ZONE);
    }

    public static Instant parse(String input, ZoneId zone) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();

        if (ISO_PATTERN.matcher(trimmed).find()) {
            try {
                // If no timezone, assume DEFAULT_ZONE
                if (!trimmed.endsWith("Z") && !trimmed.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                    return LocalDateTime.parse(trimmed.replace(' ', 'T'))
                            .atZone(zone)
                            .toInstant();
                }
                return Instant.parse(trimmed);
            } catch (DateTimeParseException ignored) {
            }
        }

        Matcher m = DATE_TIME_PATTERN.matcher(trimmed);
        if (m.matches()) {
            int day = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(4));
            int minute = Integer.parseInt(m.group(5));
            return LocalDateTime.of(year, month, day, hour, minute)
                    .atZone(zone)
                    .toInstant();
        }

        m = DATE_ONLY_PATTERN.matcher(trimmed);
        if (m.matches()) {
            int day = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            return LocalDate.of(year, month, day)
                    .atStartOfDay(zone)
                    .toInstant();
        }

        m = TIME_PATTERN.matcher(trimmed);
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            LocalDate today = LocalDate.now(zone);
            return LocalDateTime.of(today, LocalTime.of(hour, minute))
                    .atZone(zone)
                    .toInstant();
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("сегодня") || lower.contains("today")) {
            return LocalDate.now(zone).atStartOfDay(zone).toInstant();
        }
        if (lower.contains("завтра") || lower.contains("tomorrow")) {
            return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT);
            return LocalDateTime.parse(trimmed, formatter).atZone(zone).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);
            return LocalDate.parse(trimmed, formatter).atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    public static Instant parseWithDefaultTime(String dateInput, String timeInput, ZoneId zone) {
        Instant date = parse(dateInput, zone);
        if (date == null) {
            return null;
        }
        if (timeInput == null || timeInput.isBlank()) {
            return date;
        }
        LocalDateTime ldt = date.atZone(zone).toLocalDateTime();
        Matcher m = TIME_PATTERN.matcher(timeInput.trim());
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            return ldt.withHour(hour).withMinute(minute).atZone(zone).toInstant();
        }
        return date;
    }
}