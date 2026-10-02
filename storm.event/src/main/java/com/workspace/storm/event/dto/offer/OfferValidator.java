package com.workspace.storm.event.dto.offer;

import java.time.Instant;

public final class OfferValidator {

    private OfferValidator() {
    }

    public static boolean isValid(Offer offer) {
        return dropReason(offer) == null;
    }

    public static String dropReason(Offer offer) {
        if (offer == null) {
            return "null_offer";
        }
        if (offer.getDepartureAt() != null && offer.getDepartureAt().isBefore(Instant.now())) {
            return "past_departure";
        }
        if (offer.getSeats() != null && offer.getSeats() <= 0) {
            return "sold_out";
        }
        if (offer.getArrivalAt() != null && offer.getDepartureAt() != null
                && offer.getArrivalAt().isBefore(offer.getDepartureAt())) {
            return "arrival_before_departure";
        }
        return null;
    }
}
