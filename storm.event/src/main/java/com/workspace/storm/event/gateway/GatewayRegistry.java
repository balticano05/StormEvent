package com.workspace.storm.event.gateway;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GatewayRegistry {

    private final Map<String, SourceGateway> bySourceId = new LinkedHashMap<>();

    public GatewayRegistry(Collection<SourceGateway> gateways) {
        for (SourceGateway gateway : gateways) {
            if (bySourceId.put(gateway.sourceId(), gateway) != null) {
                throw new IllegalStateException("Duplicate sourceId: " + gateway.sourceId());
            }
        }
    }

    public SourceGateway get(String sourceId) {
        return bySourceId.get(sourceId);
    }

    public Collection<SourceGateway> all() {
        return Collections.unmodifiableCollection(bySourceId.values());
    }

    public int size() {
        return bySourceId.size();
    }
}
