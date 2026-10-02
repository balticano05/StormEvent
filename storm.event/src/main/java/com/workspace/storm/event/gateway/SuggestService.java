package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.source.PrincipalResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Объединяет suggest транспортных гейтвеев, топ-N (АНП-75), дедуп по имени. */
public class SuggestService {

    private static final int DEFAULT_LIMIT = 5;

    private final List<SuggestProvider> providers;
    private final int limit;

    public SuggestService(List<SuggestProvider> providers) {
        this(providers, DEFAULT_LIMIT);
    }

    public SuggestService(List<SuggestProvider> providers, int limit) {
        this.providers = List.copyOf(providers);
        this.limit = limit;
    }

    public List<StationSuggestion> suggest(String query) {
        Map<String, StationSuggestion> dedup = new LinkedHashMap<>();
        for (SuggestProvider provider : providers) {
            try {
                PrincipalResult<List<StationSuggestion>> result = provider.suggest(query);
                if (result.getData() != null) {
                    for (StationSuggestion s : result.getData()) {
                        dedup.putIfAbsent(s.name(), s);
                    }
                }
            } catch (Exception ignored) {
                // один источник не отвечает — предлагаем остальные
            }
        }
        return dedup.values().stream().limit(limit).toList();
    }
}
