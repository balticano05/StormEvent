package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** v1 ranking (ADR-051 draft): CHEAPEST / FASTEST / BEST. */
public final class Ranker {

    public enum Mode { CHEAPEST, FASTEST, BEST }

    private Ranker() {
    }

    public static List<Offer> topN(List<Offer> offers, Mode mode, int maxOffers) {
        List<Offer> sorted = new ArrayList<>(offers);
        sorted.sort(comparator(mode));
        return maxOffers > 0 && sorted.size() > maxOffers ? sorted.subList(0, maxOffers) : sorted;
    }

    public static Comparator<Offer> comparator(Mode mode) {
        return switch (mode) {
            case CHEAPEST -> Comparator.comparing(
                    o -> o.getPrice() != null && o.getPrice().getAmountByn() != null
                            ? o.getPrice().getAmountByn() : new BigDecimal(Long.MAX_VALUE));
            case FASTEST -> Comparator.comparing(Ranker::duration);
            case BEST -> Comparator.<Offer>comparingDouble(Ranker::bestScore).reversed();
        };
    }

    private static long duration(Offer o) {
        if (o.getDepartureAt() != null && o.getArrivalAt() != null) {
            return Duration.between(o.getDepartureAt(), o.getArrivalAt()).toMinutes();
        }
        return Long.MAX_VALUE;
    }

    private static double bestScore(Offer o) {
        double price = o.getPrice() != null && o.getPrice().getAmountByn() != null
                ? o.getPrice().getAmountByn().doubleValue() : Double.MAX_VALUE;
        double hours = duration(o) / 60.0;
        double direct = 1.0;
        return 0.5 * (1.0 / (1.0 + price / 100.0)) + 0.3 * (1.0 / (1.0 + hours / 5.0)) + 0.2 * direct;
    }
}
