package com.workspace.storm.event.normalize.source;

import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.OfferIdFactory;
import com.workspace.storm.event.dto.offer.OfferNormalizer;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.dto.offer.TransportOffer;
import com.workspace.storm.event.entity.atlas.AtlasRide;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import com.workspace.storm.event.parser.DateParser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AtlasOfferMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private AtlasOfferMapper() {
    }

    public static List<TransportOffer> map(AtlasSearchResult result) {
        if (result == null || result.getRides() == null) {
            return List.of();
        }
        List<TransportOffer> offers = new ArrayList<>();
        for (AtlasRide ride : result.getRides()) {
            TransportOffer offer = mapRide(ride);
            if (offer != null && OfferNormalizer.isRequiredFieldPresent(offer)) {
                offers.add(offer);
            }
        }
        return offers;
    }

    public static TransportOffer mapRide(AtlasRide ride) {
        if (ride == null) {
            return null;
        }
        TransportOffer offer = new TransportOffer();
        offer.setId(OfferIdFactory.createTransport("atlas", ride.getRideId() != null ? ride.getRideId() : ride.getId()));
        offer.setDomain(OfferDomain.TRANSPORT);
        offer.setSource("atlas");
        offer.setKind("bus");
        offer.setFrom(ride.getFrom() != null ? OfferNormalizer.normalizeCity(ride.getFrom().getDesc()) : null);
        offer.setTo(ride.getTo() != null ? OfferNormalizer.normalizeCity(ride.getTo().getDesc()) : null);
        offer.setDepartureAt(parseDateTime(ride.getDeparture()));
        offer.setArrivalAt(parseDateTime(ride.getArrival()));

        BigDecimal priceValue = ride.getOnlinePrice() != null ? ride.getOnlinePrice() : ride.getPrice();
        String currency = ride.getCurrency() != null ? ride.getCurrency() : "BYN";
        Price price = OfferNormalizer.normalizePrice(priceValue != null ? priceValue.toPlainString() + " " + currency : null, "atlas");
        offer.setPrice(price);

        offer.setSeats(ride.getFreeSeats() > 0 ? ride.getFreeSeats() : null);
        offer.setLink("https://atlasbus.by/ride/" + (ride.getRideId() != null ? ride.getRideId() : ride.getId()));

        offer.setAttributes(Map.of(
                "carrier", ride.getCarrier(),
                "carrierId", ride.getCarrierID(),
                "rideType", ride.getRideType(),
                "distance", ride.getDistance(),
                "partial", false,
                "onlineRefund", ride.isOnlineRefund(),
                "baggageAllowed", ride.isBaggageAllowed(),
                "seatingRequired", ride.isSeatingRequired()
        ));

        offer.setBusType(ride.getBus() != null ? ride.getBus().getModel() : null);
        offer.setCarrier(ride.getCarrier());
        offer.setTrainNumber(ride.getRouteId());
        offer.setTransportClass(ride.getRideType());

        return offer;
    }

    private static Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        return DateParser.parse(dateTimeStr, ZONE);
    }

    public static boolean isPartial(AtlasSearchResult result) {
        return result != null && result.isPartial();
    }
}