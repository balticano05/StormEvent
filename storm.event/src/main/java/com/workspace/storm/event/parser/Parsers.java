package com.workspace.storm.event.parser;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Shared HTML-scraping primitives.
 *
 * <p>Every helper here is null-safe by construction: a value that cannot be read
 * from the markup yields {@link Optional#empty()} instead of null, so a missing
 * cell or a non-numeric price can never reach a caller as a surprise null.
 */
final class Parsers {

    private static final char NBSP = '\u00A0';

    private Parsers() {
    }

    static Element cell(List<Element> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : null;
    }

    static Optional<String> text(Element el) {
        return Optional.ofNullable(el)
                .map(Element::text)
                .flatMap(Parsers::clean);
    }

    static Optional<String> ownText(Element el) {
        return Optional.ofNullable(el)
                .map(Element::ownText)
                .flatMap(Parsers::clean);
    }

    static Optional<String> attr(Element el, String name) {
        return Optional.ofNullable(el)
                .map(e -> e.attr(name))
                .flatMap(Parsers::clean);
    }

    static Optional<String> clean(String raw) {
        return Optional.ofNullable(raw)
                .map(s -> s.replace(NBSP, ' ').replaceAll("\\s+", " ").trim())
                .filter(s -> !s.isEmpty());
    }

    /**
     * Only ASCII 0-9 counts. {@link Character#isDigit(char)} also accepts other
     * Unicode digit sets (Arabic-Indic, Devanagari, ...) that
     * {@link Integer#parseInt(String)} then rejects with NumberFormatException.
     */
    static boolean isAsciiDigits(String value) {
        return !value.isEmpty() && value.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    static Optional<Integer> toInt(String raw) {
        return Optional.ofNullable(raw)
                .map(String::trim)
                .filter(Parsers::isAsciiDigits)
                .flatMap(Parsers::parseInt);
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<BigDecimal> toDecimal(String raw) {
        return Optional.ofNullable(raw)
                .map(String::trim)
                .map(s -> s.replace(',', '.'))
                .filter(s -> !s.isEmpty())
                .flatMap(Parsers::parseDecimal);
    }

    private static Optional<BigDecimal> parseDecimal(String value) {
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Elements directTds(Element row) {
        Elements tds = new Elements();
        if (row == null) {
            return tds;
        }
        for (Element child : row.children()) {
            if ("td".equals(child.tagName())) {
                tds.add(child);
            }
        }
        return tds;
    }

}
