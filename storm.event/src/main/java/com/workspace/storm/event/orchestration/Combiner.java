package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges tool results: dedup by offer id, empty is fine, groups by domain. */
public final class Combiner {

    private Combiner() {
    }

    public static List<Offer> merge(List<ToolResult> results) {
        Map<String, Offer> byId = new LinkedHashMap<>();
        for (ToolResult result : results) {
            for (Offer offer : result.offers()) {
                if (offer.getId() != null) {
                    byId.putIfAbsent(offer.getId(), offer);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    public static Map<String, List<Offer>> groupByDomain(List<ToolResult> results) {
        Map<String, List<Offer>> byDomain = new LinkedHashMap<>();
        for (ToolResult result : results) {
            byDomain.computeIfAbsent(result.domain(), k -> new ArrayList<>()).addAll(result.offers());
        }
        return byDomain;
    }
}
