package com.workspace.storm.event.normalize.source;

import com.workspace.storm.event.dto.offer.EventOffer;
import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.OfferIdFactory;
import com.workspace.storm.event.dto.offer.OfferNormalizer;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.entity.ticket.Event;
import com.workspace.storm.event.entity.ticket.Offer;
import com.workspace.storm.event.parser.DateParser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TicketProOfferMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private TicketProOfferMapper() {
    }

    public static List<EventOffer> map(List<Event> events) {
        if (events == null) {
            return List.of();
        }
        List<EventOffer> offers = new ArrayList<>();
        for (Event event : events) {
            EventOffer offer = mapEvent(event);
            if (offer != null && OfferNormalizer.isRequiredFieldPresent(offer)) {
                offers.add(offer);
            }
        }
        return offers;
    }

    public static EventOffer mapEvent(Event event) {
        if (event == null) {
            return null;
        }
        EventOffer offer = new EventOffer();
        String externalKey = event.getUrl() != null ? event.getUrl().hashCode() + "" : event.getName();
        offer.setId(OfferIdFactory.createEvent("ticketpro", externalKey));
        offer.setDomain(OfferDomain.EVENT);
        offer.setSource("ticketpro");
        offer.setKind("event");
        offer.setFrom(event.getLocation() != null ? OfferNormalizer.normalizeCity(event.getLocation().getAddress() != null ? event.getLocation().getAddress().getAddressLocality() : null) : null);
        offer.setTo(event.getLocation() != null ? OfferNormalizer.normalizeCity(event.getLocation().getName()) : null);
        offer.setDepartureAt(parseDateTime(event.getStartDate()));
        offer.setArrivalAt(parseDateTime(event.getEndDate()));

        com.workspace.storm.event.entity.ticket.Offer tpOffer = event.getOffers();
        BigDecimal priceValue = tpOffer != null ? (tpOffer.getLowPrice() != null ? tpOffer.getLowPrice() : tpOffer.getPrice()) : null;
        String currency = tpOffer != null ? tpOffer.getPriceCurrency() : "BYN";
        Price price = OfferNormalizer.normalizePrice(priceValue != null ? priceValue.toPlainString() + " " + currency : null, "ticketpro");
        offer.setPrice(price);

        offer.setSeats(tpOffer != null && "available".equalsIgnoreCase(tpOffer.getAvailability()) ? 1 : 0);

        offer.setLink(tpOffer != null ? tpOffer.getUrl() : event.getUrl());

        offer.setAttributes(Map.of(
                "venue", event.getLocation() != null ? event.getLocation().getName() : null,
                "category", event.getCategoryPath(),
                "images", event.getImages(),
                "description", event.getDescription(),
                "partial", false
        ));

        offer.setVenue(event.getLocation() != null ? event.getLocation().getName() : null);
        offer.setCategory(event.getCategoryPath());
        offer.setImages(event.getImages());
        offer.setStartDate(offer.getDepartureAt());

        return offer;
    }

    private static Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        return DateParser.parse(dateTimeStr, ZONE);
    }
}