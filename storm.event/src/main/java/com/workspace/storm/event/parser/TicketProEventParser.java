package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.ticket.Address;
import com.workspace.storm.event.entity.ticket.Event;
import com.workspace.storm.event.entity.ticket.Location;
import com.workspace.storm.event.entity.ticket.Offer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TicketProEventParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Event> parseEvents(String html) {
        List<Event> events = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = mapper.readTree(script.data());
                if (isEvent(root)) {
                    events.add(mapEvent(root));
                }
            } catch (JacksonException e) {
                log.debug("Skipping malformed JSON-LD script: {}", e.getMessage());
            }
        }
        return events;
    }

    public boolean hasNextPage(String html) {
        Document doc = Jsoup.parse(html);
        return doc.selectFirst("link[rel=next]") != null;
    }

    private boolean isEvent(JsonNode node) {
        if (!node.has("@type")) {
            return false;
        }
        JsonNode t = node.get("@type");
        if (t.isArray()) {
            for (JsonNode x : t) {
                if ("Event".equalsIgnoreCase(x.asText())) {
                    return true;
                }
            }
            return false;
        }
        return "Event".equalsIgnoreCase(t.asText());
    }

    private Event mapEvent(JsonNode n) {
        Event e = new Event();
        e.setUrl(text(n, "url").orElse(null));
        e.setName(text(n, "name").orElse(null));
        e.setStartDate(text(n, "startDate").orElse(null));
        e.setEndDate(text(n, "endDate").orElse(null));
        e.setDescription(text(n, "description").orElse(null));

        if (n.has("image")) {
            JsonNode img = n.get("image");
            List<String> list = new ArrayList<>();
            if (img.isArray()) {
                img.forEach(x -> imageUrl(x).ifPresent(list::add));
            } else {
                imageUrl(img).ifPresent(list::add);
            }
            e.setImages(list);
        }

        if (n.hasNonNull("location")) {
            e.setLocation(mapLocation(n.get("location")));
        }

        if (n.hasNonNull("offers")) {
            e.setOffers(mapOffer(n.get("offers")));
        }
        return e;
    }

    private Location mapLocation(JsonNode loc) {
        Location l = new Location();
        l.setName(text(loc, "name").orElse(null));
        if (loc.hasNonNull("address")) {
            JsonNode a = loc.get("address");
            Address addr = new Address();
            addr.setStreetAddress(text(a, "streetAddress").orElse(null));
            addr.setAddressLocality(text(a, "addressLocality").orElse(null));
            addr.setAddressRegion(text(a, "addressRegion").orElse(null));
            addr.setAddressCountry(text(a, "addressCountry").orElse(null));
            l.setAddress(addr);
        }
        return l;
    }

    private Offer mapOffer(JsonNode o) {
        Offer off = new Offer();
        off.setAvailability(text(o, "availability").orElse(null));
        off.setPrice(decimal(o, "price").orElse(null));
        off.setLowPrice(decimal(o, "lowPrice").orElse(null));
        off.setHighPrice(decimal(o, "highPrice").orElse(null));
        off.setPriceCurrency(text(o, "priceCurrency").orElse(null));
        off.setUrl(text(o, "url").orElse(null));
        off.setValidFrom(text(o, "validFrom").orElse(null));
        return off;
    }

    private Optional<String> imageUrl(JsonNode img) {
        if (img.isObject()) {
            Optional<String> id = text(img, "@id");
            if (id.isPresent()) return id;
            return text(img, "url");
        }
        return Optional.of(img.asText()).filter(s -> !s.isEmpty());
    }

    private static Optional<String> text(JsonNode n, String field) {
        return n.hasNonNull(field) ? Optional.of(n.get(field).asText()) : Optional.empty();
    }

    private static Optional<BigDecimal> decimal(JsonNode n, String field) {
        if (!n.hasNonNull(field)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(n.get(field).asText()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

}