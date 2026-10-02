package com.workspace.storm.event.tool;

import com.workspace.storm.event.dto.offer.Offer;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class ToolFilter {

    private ToolFilter() {
    }

    /** Sorts by amountByn ascending, optional budget cap, optional top-N. */
    public static List<Offer> apply(List<Offer> offers, boolean directOnly, BigDecimal budgetByn, Integer topN) {
        Comparator<Offer> byPrice = Comparator.comparing(
                o -> o.getPrice() != null && o.getPrice().getAmountByn() != null ? o.getPrice().getAmountByn() : new BigDecimal(Long.MAX_VALUE),
                Comparator.nullsLast(BigDecimal::compareTo));
        List<Offer> out = offers.stream()
                .filter(o -> budgetByn == null
                        || (o.getPrice() != null && o.getPrice().getAmountByn() != null && o.getPrice().getAmountByn().compareTo(budgetByn) <= 0))
                .sorted(byPrice)
                .toList();
        return topN != null && topN > 0 && out.size() > topN ? out.subList(0, topN) : out;
    }
}
