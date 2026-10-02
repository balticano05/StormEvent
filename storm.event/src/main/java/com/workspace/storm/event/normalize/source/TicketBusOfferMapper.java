package com.workspace.storm.event.normalize.source;

import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.OfferIdFactory;
import com.workspace.storm.event.dto.offer.OfferNormalizer;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.dto.offer.TransportOffer;
import com.workspace.storm.event.entity.tb.TbRace;
import com.workspace.storm.event.parser.DateParser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TicketBusOfferMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private TicketBusOfferMapper() {
    }

    public static List<TransportOffer> map(List<TbRace> races) {
        if (races == null) {
            return List.of();
        }
        List<TransportOffer> offers = new ArrayList<>();
        for (TbRace race : races) {
            TransportOffer offer = mapRace(race);
            if (offer != null && OfferNormalizer.isRequiredFieldPresent(offer)) {
                offers.add(offer);
            }
        }
        return offers;
    }

    public static TransportOffer mapRace(TbRace race) {
        if (race == null) {
            return null;
        }
        TransportOffer offer = new TransportOffer();
        offer.setId(OfferIdFactory.createTransport("ticketbus", race.getCode()));
        offer.setDomain(OfferDomain.TRANSPORT);
        offer.setSource("ticketbus");
        offer.setKind("bus");
        offer.setFrom(OfferNormalizer.normalizeCity(race.getRoute() != null ? race.getRoute().split(" - ")[0] : null));
        offer.setTo(OfferNormalizer.normalizeCity(race.getRoute() != null && race.getRoute().contains(" - ")
                ? race.getRoute().split(" - ")[1] : null));
        offer.setDepartureAt(parseDateTime(race.getDeparture()));
        offer.setArrivalAt(parseDateTime(race.getArrival()));

        Price price = OfferNormalizer.normalizePrice(race.getPrice() != null ? race.getPrice().toPlainString() + " BYN" : null, "ticketbus");
        offer.setPrice(price);

        offer.setLink("https://ticketbus.by/race/" + race.getCode());

        offer.setAttributes(Map.of(
                "busModel", race.getBusModel(),
                "seatType", race.getSeatType(),
                "carrier", race.getCarrier(),
                "partial", false
        ));

        offer.setBusType(race.getBusModel());
        offer.setCarrier(race.getCarrier());
        offer.setTrainNumber(race.getCode());
        offer.setTransportClass(race.getSeatType());

        return offer;
    }

    private static Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        return DateParser.parse(dateTimeStr, ZONE);
    }
}