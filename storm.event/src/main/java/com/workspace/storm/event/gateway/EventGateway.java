package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;

public interface EventGateway extends SourceGateway {
    PrincipalResult<java.util.List<Offer>> search(EventQuery q);
}
