package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.Hotel;
import com.workspace.storm.event.entity.RoomOffer;
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
        Map<String, Hotel> byName = new LinkedHashMap<>();

        for (Element row : doc.select("table.tab5x tr.tr_hover")) {
            Elements tds = directTds(row);
            if (tds.size() < 8) continue;

            Element hotelTd = tds.get(1);
            Element hotelLink = hotelTd.selectFirst("a.dashed");
            if (hotelLink == null) continue;

            String hotelName = hotelLink.text().trim();
            if (hotelName.isEmpty()) continue;

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
        String own = hotelTd.ownText().trim();
        if (own.startsWith("-")) own = own.substring(1).trim();
        return own.isEmpty() ? Optional.empty() : Optional.of(own);
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

        Element roomTd = tds.get(4);
        Element roomB = roomTd.selectFirst("b");
        offer.setRoomType(roomB != null ? roomB.text().trim() : roomTd.text().trim());
        offer.setCapacity(tds.get(3).select("img[src*=p_one], img[src*=p_dop]").size());

        Optional.ofNullable(tds.get(2).selectFirst("img"))
                .map(img -> img.absUrl("src"))
                .filter(s -> !s.isEmpty())
                .ifPresent(offer::setImageUrl);

        String priceText = tds.get(5).text().trim();
        offer.setPrice(parsePrice(priceText).orElse(null));
        offer.setCurrency(parseCurrency(priceText).orElse(null));

        Element breakfastTd = tds.get(6);
        String breakfastTitle = breakfastTd.attr("title");
        offer.setBreakfastIncluded(
                breakfastTitle.toLowerCase(Locale.ROOT).contains(BREAKFAST_INCLUDED_MARKER));

        Optional.ofNullable(tds.get(7).selectFirst("a"))
                .map(a -> a.absUrl("href"))
                .filter(s -> !s.isEmpty())
                .ifPresent(offer::setBookingUrl);

        return offer;
    }

    private Optional<BigDecimal> parsePrice(String raw) {
        if (raw == null) return Optional.empty();
        Matcher m = PRICE_PATTERN.matcher(raw);
        if (!m.find()) return Optional.empty();
        String cleaned = m.group().replaceAll("[\\s\\u00A0]", "").replace(',', '.');
        try {
            return Optional.of(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<String> parseCurrency(String raw) {
        if (raw == null) return Optional.empty();
        if (raw.contains("BYN")) return Optional.of("BYN");
        if (raw.contains("RUB")) return Optional.of("RUB");
        if (raw.contains("EUR")) return Optional.of("EUR");
        return Optional.empty();
    }

    private Elements directTds(Element row) {
        Elements tds = new Elements();
        for (Element child : row.children()) {
            if ("td".equals(child.tagName())) {
                tds.add(child);
            }
        }
        return tds;
    }

}