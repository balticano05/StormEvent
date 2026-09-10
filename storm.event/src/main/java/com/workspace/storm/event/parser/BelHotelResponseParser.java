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
import java.util.Map;

@Component
public class BelHotelResponseParser {

    private static final String BREAKFAST_INCLUDED_MARKER = "включ";

    public List<Hotel> parse(Document doc, Long cityId) {
        Map<String, Hotel> byName = new LinkedHashMap<>();

        for (Element row : doc.select("table.tab5x tr.tr_hover")) {
            Elements tds = directTds(row);
            if (tds.size() < 8) continue;

            Element hotelTd = tds.get(1);
            Element hotelLink = hotelTd.selectFirst("a.dashed");
            if (hotelLink == null) continue;

            String hotelName = hotelLink.text().trim();

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
        h.setType(extractType(hotelTd));
        h.setCategory(extractCategory(hotelTd));
        h.setStars(extractStars(hotelTd));
        h.setOffers(new ArrayList<>());
        return h;
    }

    private String extractType(Element hotelTd) {
        String own = hotelTd.ownText().trim();
        if (own.startsWith("-")) own = own.substring(1).trim();
        return own;
    }

    private String extractCategory(Element hotelTd) {
        Element span = hotelTd.selectFirst("span[style*=slategray]");
        return span != null ? span.text().trim() : null;
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

        Element img = tds.get(2).selectFirst("img");
        if (img != null) offer.setImageUrl(img.absUrl("src"));

        String priceText = tds.get(5).text().trim();
        offer.setPrice(parsePrice(priceText));
        offer.setCurrency(parseCurrency(priceText));

        Element breakfastTd = tds.get(6);
        String breakfastTitle = breakfastTd.attr("title");
        offer.setBreakfastIncluded(
                breakfastTitle.toLowerCase().contains(BREAKFAST_INCLUDED_MARKER));

        Element bookingLink = tds.get(7).selectFirst("a");
        if (bookingLink != null) offer.setBookingUrl(bookingLink.absUrl("href"));

        return offer;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.replaceAll("[^0-9,.]", "").replace(',', '.');
        if (cleaned.isBlank()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseCurrency(String raw) {
        if (raw == null) return null;
        if (raw.contains("BYN")) return "BYN";
        if (raw.contains("RUB")) return "RUB";
        if (raw.contains("EUR")) return "EUR";
        return null;
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