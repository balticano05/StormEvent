package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;

public interface TransportGateway extends SourceGateway {
    PrincipalResult<java.util.List<Offer>> search(TransportQuery q);
    PrincipalResult<java.util.List<StationSuggestion>> suggest(String query);
}
