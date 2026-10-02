package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.TicketBusSearchRequest;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.entity.tb.TbRace;
import com.workspace.storm.event.entity.tb.TbStation;
import com.workspace.storm.event.normalize.source.TicketBusOfferMapper;
import com.workspace.storm.event.service.TicketBusService;

import java.util.ArrayList;
import java.util.List;

public class TicketBusGateway implements TransportGateway {

    private final TicketBusService service;
    private final SourceStateGuard guard;
    private final SourceMetrics metrics;

    public TicketBusGateway(TicketBusService service, SourceStateGuard guard, SourceMetrics metrics) {
        this.service = service;
        this.guard = guard;
        this.metrics = metrics;
    }

    @Override
    public String sourceId() { return "ticketbus"; }

    @Override
    public SourceKind kind() { return SourceKind.BUS; }

    @Override
    public boolean supportsSuggest() { return true; }

    @Override
    public PrincipalResult<List<Offer>> search(TransportQuery q) {
        long start = System.currentTimeMillis();
        if (!guard.isCallable(sourceId())) {
            return PrincipalResult.skipped(sourceId(), "source disabled");
        }
        try {
            TicketBusSearchRequest req = new TicketBusSearchRequest();
            req.setFromId(resolveId(q.from(), true));
            req.setToId(resolveId(q.to(), true));
            req.setDate(q.date());
            List<TbRace> races = service.searchRaces(req);
            List<Offer> offers = new ArrayList<>(TicketBusOfferMapper.map(races));
            long duration = System.currentTimeMillis() - start;
            metrics.record(sourceId(), "OK", duration);
            return PrincipalResult.ok(sourceId(), offers, duration);
        } catch (Exception e) {
            return AtlasGateway.failure(sourceId(), e, start);
        }
    }

    private String resolveId(String query, boolean fromCity) {
        List<TbStation> stations = service.findStations(query, null, fromCity);
        return stations.isEmpty() ? query : stations.get(0).getId();
    }

    @Override
    public PrincipalResult<List<StationSuggestion>> suggest(String query) {
        try {
            List<TbStation> stations = service.findStations(query, null, true);
            List<StationSuggestion> out = new ArrayList<>();
            for (TbStation s : stations) {
                out.add(new StationSuggestion(sourceId(), s.getName(), s.getId(), "station"));
            }
            return PrincipalResult.ok(sourceId(), out, 0L);
        } catch (Exception e) {
            return AtlasGateway.failure(sourceId(), e, System.currentTimeMillis());
        }
    }
}
