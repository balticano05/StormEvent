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
            readTree(script.data()).filter(this::isEvent).map(this::mapEvent).ifPresent(events::add);
        }
        return events;
    }

    public boolean hasNextPage(String html) {
        Document doc = Jsoup.parse(html);
        return doc.selectFirst("link[rel=next]") != null;
    }

    private Optional<JsonNode> readTree(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readTree(json));
        } catch (JacksonException e) {
            log.debug("Skipping malformed JSON-LD script: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isEvent(JsonNode node) {
        Optional<JsonNode> type = Optional.ofNullable(node.get("@type"));
        if (type.isEmpty()) {
            return false;
        }
        JsonNode t = type.get();
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

        node(n, "image").ifPresent(img -> {
            List<String> list = new ArrayList<>();
            if (img.isArray()) {
                img.forEach(x -> imageUrl(x).ifPresent(list::add));
            } else {
                imageUrl(img).ifPresent(list::add);
            }
            e.setImages(list);
        });

        node(n, "location").map(this::mapLocation).ifPresent(e::setLocation);
        node(n, "offers").map(this::mapOffer).ifPresent(e::setOffers);
        return e;
    }

    private Location mapLocation(JsonNode loc) {
        Location l = new Location();
        l.setName(text(loc, "name").orElse(null));
        node(loc, "address").ifPresent(a -> l.setAddress(mapAddress(a)));
        return l;
    }

    private Address mapAddress(JsonNode a) {
        Address addr = new Address();
        addr.setStreetAddress(text(a, "streetAddress").orElse(null));
        addr.setAddressLocality(text(a, "addressLocality").orElse(null));
        addr.setAddressRegion(text(a, "addressRegion").orElse(null));
        addr.setAddressCountry(text(a, "addressCountry").orElse(null));
        return addr;
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

    private static Optional<JsonNode> node(JsonNode n, String field) {
        return Optional.ofNullable(n.get(field)).filter(node -> !node.isNull());
    }

    private static Optional<String> text(JsonNode n, String field) {
        return node(n, field).map(JsonNode::asText);
    }

    private static Optional<BigDecimal> decimal(JsonNode n, String field) {
        return node(n, field).flatMap(TicketProEventParser::toDecimal);
    }

    private static Optional<BigDecimal> toDecimal(JsonNode node) {
        try {
            return Optional.of(new BigDecimal(node.asText()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

}