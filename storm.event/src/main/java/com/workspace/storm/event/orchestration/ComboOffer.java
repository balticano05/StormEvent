package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;

import java.math.BigDecimal;
import java.util.List;

public record ComboOffer(List<Offer> items, BigDecimal totalPriceByn, String tag) {

    public ComboOffer(Offer single) {
        this(List.of(single), single.getPrice() != null ? single.getPrice().getAmountByn() : null, single.getKind());
    }
}
