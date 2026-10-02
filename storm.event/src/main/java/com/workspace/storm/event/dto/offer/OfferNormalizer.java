package com.workspace.storm.event.dto.offer;

import com.workspace.storm.event.parser.DateParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfferNormalizer {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Minsk");
    private static final Pattern PRICE_PATTERN = Pattern.compile("([\\d\\s.,]+)\\s*(BYN|RUB|USD|EUR)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile("(?:от|from)\\s+([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal RUB_TO_BYN = new BigDecimal("0.032");
    private static final BigDecimal USD_TO_BYN = new BigDecimal("3.25");
    private static final BigDecimal EUR_TO_BYN = new BigDecimal("3.50");

    private OfferNormalizer() {
    }

    public static Price normalizePrice(String priceStr, String source) {
        if (priceStr == null || priceStr.isBlank()) {
            return null;
        }
        String cleaned = priceStr.replaceAll("\\s+", " ").trim();

        Matcher rangeMatcher = PRICE_RANGE_PATTERN.matcher(cleaned);
        if (rangeMatcher.find()) {
            cleaned = rangeMatcher.group(1);
        }

        Matcher matcher = PRICE_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }

        String amountStr = matcher.group(1).replace(',', '.').replaceAll("\\s", "");
        String currencyCode = matcher.group(2);

        try {
            BigDecimal amount = new BigDecimal(amountStr);

            if ("bzd".equalsIgnoreCase(source) && amount.compareTo(new BigDecimal("1000")) > 0) {
                amount = amount.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }

            Currency currency = currencyCode != null ? Currency.getInstance(currencyCode.toUpperCase()) : Currency.getInstance("BYN");
            BigDecimal amountByn = convertToByn(amount, currency);

            return Price.of(amount, currency, amountByn);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal convertToByn(BigDecimal amount, Currency currency) {
        String code = currency.getCurrencyCode();
        return switch (code) {
            case "BYN" -> amount;
            case "RUB" -> amount.multiply(RUB_TO_BYN).setScale(2, RoundingMode.HALF_UP);
            case "USD" -> amount.multiply(USD_TO_BYN).setScale(2, RoundingMode.HALF_UP);
            case "EUR" -> amount.multiply(EUR_TO_BYN).setScale(2, RoundingMode.HALF_UP);
            default -> amount;
        };
    }

    public static Instant normalizeDateTime(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.isBlank()) {
            if (timeStr == null || timeStr.isBlank()) {
                return null;
            }
            String todayStr = LocalDate.now(DEFAULT_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            return DateParser.parseWithDefaultTime(todayStr, timeStr, DEFAULT_ZONE);
        }
        return DateParser.parseWithDefaultTime(dateStr, timeStr, DEFAULT_ZONE);
    }

    public static Instant normalizeDateTime(String dateTimeStr) {
        return DateParser.parse(dateTimeStr, DEFAULT_ZONE);
    }

    public static String normalizeCity(String city) {
        if (city == null) {
            return null;
        }
        return city.trim()
                .replaceAll("\\s+", " ")
                .replace("Мінск", "Минск")
                .replace("Минск", "Минск");
    }

    public static String normalizeKey(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase(Locale.ROOT)
                .replace("м", "m")
                .replace("и", "i")
                .replace("н", "n")
                .replace("с", "s")
                .replace("к", "k")
                .replace("а", "a")
                .replace("о", "o")
                .replace("е", "e")
                .replace("р", "r")
                .replace("т", "t")
                .replace("ь", "")
                .replace("ъ", "")
                .replace("ё", "e")
                .replace("і", "i")
                .replace("ы", "y")
                .replace("у", "u")
                .replace("д", "d")
                .replace("г", "g")
                .replace("х", "h")
                .replace("в", "v")
                .replace("з", "z")
                .replace("й", "y")
                .replace("л", "l")
                .replace("п", "p")
                .replace("ш", "sh")
                .replace("щ", "sch")
                .replace("ч", "ch")
                .replace("ц", "ts")
                .replace("я", "ya")
                .replace("ю", "yu")
                .replace("э", "e")
                .replaceAll("[\\s\\-_.]+", "");
    }

    public static boolean isRequiredFieldPresent(Offer offer) {
        return offer != null
                && offer.getFrom() != null && !offer.getFrom().isBlank()
                && offer.getTo() != null && !offer.getTo().isBlank()
                && offer.getDepartureAt() != null
                && offer.getPrice() != null && offer.getPrice().getAmount() != null
                && offer.getPrice().getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isRequiredFieldPresent(TransportOffer offer) {
        return offer != null
                && offer.getFrom() != null && !offer.getFrom().isBlank()
                && offer.getTo() != null && !offer.getTo().isBlank()
                && offer.getDepartureAt() != null
                && offer.getPrice() != null && offer.getPrice().getAmount() != null
                && offer.getPrice().getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isRequiredFieldPresent(EventOffer offer) {
        return offer != null
                && offer.getFrom() != null && !offer.getFrom().isBlank()
                && offer.getTo() != null && !offer.getTo().isBlank()
                && offer.getDepartureAt() != null
                && offer.getPrice() != null && offer.getPrice().getAmount() != null
                && offer.getPrice().getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isRequiredFieldPresent(HotelOffer offer) {
        return offer != null
                && offer.getFrom() != null && !offer.getFrom().isBlank()
                && offer.getTo() != null && !offer.getTo().isBlank()
                && offer.getDepartureAt() != null
                && offer.getPrice() != null && offer.getPrice().getAmount() != null
                && offer.getPrice().getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public static Optional<String> validateAndGetDropReason(Offer offer) {
        if (offer == null) {
            return Optional.of("null_offer");
        }
        if (offer.getFrom() == null || offer.getFrom().isBlank()) {
            return Optional.of("missing_from");
        }
        if (offer.getTo() == null || offer.getTo().isBlank()) {
            return Optional.of("missing_to");
        }
        if (offer.getDepartureAt() == null) {
            return Optional.of("missing_departure");
        }
        if (offer.getPrice() == null || offer.getPrice().getAmount() == null) {
            return Optional.of("missing_price");
        }
        if (offer.getPrice().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of("invalid_price");
        }
        if (offer.getSeats() != null && offer.getSeats() <= 0) {
            return Optional.of("invalid_seats");
        }
        if (offer.getDepartureAt().isBefore(Instant.now())) {
            return Optional.of("past_departure");
        }
        return Optional.empty();
    }
}