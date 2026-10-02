package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.source.PrincipalResult;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class SuggestServiceTest {

    private static SuggestProvider provider(String sourceId, java.util.function.Function<String, PrincipalResult<List<StationSuggestion>>> fn) {
        return new SuggestProvider() {
            @Override public String sourceId() { return sourceId; }
            @Override public PrincipalResult<List<StationSuggestion>> suggest(String query) {
                return fn.apply(query);
            }
        };
    }

    @Test
    void mergesAndDeduplicatesByName() {
        SuggestService svc = new SuggestService(List.of(
                provider("atlasbus", q -> PrincipalResult.ok("atlasbus", List.of(
                        new StationSuggestion("atlasbus", "Минск", "1", "station"),
                        new StationSuggestion("atlasbus", "Гродно", "2", "station")), 0L)),
                provider("bzd", q -> PrincipalResult.ok("bzd", List.of(
                        new StationSuggestion("bzd", "Минск", "3", "station"),
                        new StationSuggestion("bzd", "Брест", "4", "station")), 0L))));
        List<StationSuggestion> out = svc.suggest("ми");
        assertEquals(3, out.size());
        assertEquals("Минск", out.get(0).name());
    }

    @Test
    void limitsToTopFive() {
        SuggestService svc = new SuggestService(List.of(
                provider("atlasbus", q -> PrincipalResult.ok("atlasbus", java.util.stream.IntStream.range(0, 10)
                        .mapToObj(i -> new StationSuggestion("atlasbus", "Город" + i, String.valueOf(i), "station")).toList(), 0L)),
                provider("bzd", q -> PrincipalResult.ok("bzd", List.of(), 0L))));
        assertEquals(5, svc.suggest("г").size());
    }

    @Test
    void providerFailureDoesNotBreakOthers() {
        SuggestService svc = new SuggestService(List.of(
                provider("atlasbus", q -> { throw new RuntimeException("down"); }),
                provider("bzd", q -> PrincipalResult.ok("bzd", List.of(new StationSuggestion("bzd", "Минск", "1", "station")), 0L))));
        assertEquals(1, svc.suggest("ми").size());
    }
}
