package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.hotel.Hotel;
import com.workspace.storm.event.entity.hotel.RoomOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BelHotelResponseParser {

    private static final String BREAKFAST_INCLUDED_MARKER = "включ";
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("\\d[\\d\\s\\u00A0]*(?:[.,]\\d+)?");

    public List<Hotel> parse(Document doc, Long cityId) {
        if (doc == null) {
            return List.of();
        }
        Map<String, Hotel> byName = new LinkedHashMap<>();

        for (Element row : doc.select("table.tab5x tr.tr_hover")) {
            Elements tds = Parsers.directTds(row);
            if (tds.size() < 8) continue;

            Element hotelTd = Parsers.cell(tds, 1);
            Element hotelLink = hotelTd == null ? null : hotelTd.selectFirst("a.dashed");
            if (hotelLink == null) continue;

            String hotelName = Parsers.clean(hotelLink.text()).orElse(null);
            if (hotelName == null) continue;

            Hotel hotel = byName.computeIfAbsent(hotelName, name -> createHotel(hotelTd, name));
            hotel.getOffers().add(parseOffer(tds));
        }

        List<Hotel> result = new ArrayList<>(byName.values());
        result.forEach(h -> h.setCityId(cityId));
        return result;
    }

    private Hotel createHotel(Element hotelTd, String name) {
        Hotel h = new Hotel();
        h.setId(name);
        h.setName(name);
        h.setType(extractType(hotelTd).orElse(null));
        h.setCategory(extractCategory(hotelTd).orElse(null));
        h.setStars(extractStars(hotelTd));
        h.setOffers(new ArrayList<>());
        return h;
    }

    private Optional<String> extractType(Element hotelTd) {
        String own = Parsers.clean(hotelTd.ownText()).orElse("");
        String type = own.startsWith("-") ? own.substring(1).trim() : own;
        return Parsers.clean(type);
    }

    private Optional<String> extractCategory(Element hotelTd) {
        return Optional.ofNullable(hotelTd.selectFirst("span[style*=slategray]"))
                .map(Element::text)
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    private int extractStars(Element hotelTd) {
        return hotelTd.select("span[style*=slategray] img[src*=star_small]").size();
    }

    private RoomOffer parseOffer(Elements tds) {
        RoomOffer offer = new RoomOffer();

        Element roomTd = Parsers.cell(tds, 4);
        Element roomB = roomTd == null ? null : roomTd.selectFirst("b");
        offer.setRoomType(roomB != null
                ? Parsers.clean(roomB.text()).orElse("")
                : Parsers.text(roomTd).orElse(""));
        offer.setCapacity(Optional.ofNullable(Parsers.cell(tds, 3))
                .map(c -> c.select("img[src*=p_one], img[src*=p_dop]").size())
                .orElse(0));

        Optional.ofNullable(Parsers.cell(tds, 2))
                .map(td -> td.selectFirst("img"))
                .map(img -> img.absUrl("src"))
                .filter(s -> !s.isEmpty())
                .ifPresent(offer::setImageUrl);

        String priceText = Parsers.text(Parsers.cell(tds, 5)).orElse("");
        offer.setPrice(parsePrice(priceText).orElse(null));
        offer.setCurrency(parseCurrency(priceText).orElse(null));

        Element breakfastTd = Parsers.cell(tds, 6);
        String breakfastTitle = Parsers.attr(breakfastTd, "title").orElse("");
        offer.setBreakfastIncluded(
                breakfastTitle.toLowerCase(Locale.ROOT).contains(BREAKFAST_INCLUDED_MARKER));

        Optional.ofNullable(Parsers.cell(tds, 7))
                .map(td -> td.selectFirst("a"))
                .map(a -> a.absUrl("href"))
                .filter(s -> !s.isEmpty())
                .ifPresent(offer::setBookingUrl);

        return offer;
    }

    private Optional<BigDecimal> parsePrice(String raw) {
        if (raw == null) return Optional.empty();
        Matcher m = PRICE_PATTERN.matcher(raw);
        if (!m.find()) return Optional.empty();
        return Parsers.toDecimal(m.group().replaceAll("[\\s\\u00A0]", ""));
    }

    private Optional<String> parseCurrency(String raw) {
        if (raw == null) return Optional.empty();
        if (raw.contains("BYN")) return Optional.of("BYN");
        if (raw.contains("RUB")) return Optional.of("RUB");
        if (raw.contains("EUR")) return Optional.of("EUR");
        return Optional.empty();
    }

}