package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.source.PrincipalResult;

public interface SuggestProvider {
    String sourceId();
    PrincipalResult<java.util.List<StationSuggestion>> suggest(String query);
}
