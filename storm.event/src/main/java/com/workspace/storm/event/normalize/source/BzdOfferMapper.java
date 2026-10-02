package com.workspace.storm.event.normalize.source;

import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.OfferIdFactory;
import com.workspace.storm.event.dto.offer.OfferNormalizer;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.dto.offer.TransportOffer;
import com.workspace.storm.event.entity.bzd.BzdCar;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import com.workspace.storm.event.parser.DateParser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BzdOfferMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private BzdOfferMapper() {
    }

    public static List<TransportOffer> map(List<BzdTrain> trains) {
        if (trains == null) {
            return List.of();
        }
        List<TransportOffer> offers = new ArrayList<>();
        for (BzdTrain train : trains) {
            TransportOffer offer = mapTrain(train);
            if (offer != null && OfferNormalizer.isRequiredFieldPresent(offer)) {
                offers.add(offer);
            }
        }
        return offers;
    }

    public static TransportOffer mapTrain(BzdTrain train) {
        if (train == null) {
            return null;
        }
        TransportOffer offer = new TransportOffer();
        offer.setId(OfferIdFactory.createTransport("bzd", train.getNumber()));
        offer.setDomain(OfferDomain.TRANSPORT);
        offer.setSource("bzd");
        offer.setKind("train");
        offer.setFrom(OfferNormalizer.normalizeCity(train.getFromName()));
        offer.setTo(OfferNormalizer.normalizeCity(train.getToName()));
        offer.setDepartureAt(parseDateTime(train.getFromTime()));
        offer.setArrivalAt(parseDateTime(train.getToTime()));

        String carType = train.getCars() != null && !train.getCars().isEmpty()
                ? train.getCars().get(0).getName() : null;

        BigDecimal priceValue = null;
        if (train.getCars() != null && !train.getCars().isEmpty()) {
            BzdCar firstCar = train.getCars().get(0);
            if (firstCar.getPriceByn() != null) {
                priceValue = firstCar.getPriceByn();
            }
        }
        Price price = OfferNormalizer.normalizePrice(priceValue != null ? priceValue.toPlainString() + " BYN" : null, "bzd");
        offer.setPrice(price);

        offer.setSeats(train.getCars() != null && !train.getCars().isEmpty()
                ? train.getCars().get(0).getSeats() : null);

        offer.setLink("https://pass.rw.by/ru/route/?train=" + train.getNumber());

        offer.setAttributes(Map.of(
                "trainType", train.getType(),
                "duration", train.getDuration(),
                "carType", carType,
                "partial", false
        ));

        offer.setBusType(null);
        offer.setCarrier("BZD");
        offer.setTrainNumber(train.getNumber());
        offer.setTransportClass(carType);

        return offer;
    }

    private static Instant parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        return DateParser.parse(dateTimeStr, ZONE);
    }
}