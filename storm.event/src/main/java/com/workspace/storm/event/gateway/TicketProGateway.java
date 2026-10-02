package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.entity.ticket.Event;
import com.workspace.storm.event.normalize.source.TicketProOfferMapper;
import com.workspace.storm.event.service.TicketProService;

import java.util.ArrayList;
import java.util.List;

public class TicketProGateway implements EventGateway {

    private final TicketProService service;
    private final SourceStateGuard guard;
    private final SourceMetrics metrics;

    public TicketProGateway(TicketProService service, SourceStateGuard guard, SourceMetrics metrics) {
        this.service = service;
        this.guard = guard;
        this.metrics = metrics;
    }

    @Override
    public String sourceId() { return "ticketpro"; }

    @Override
    public SourceKind kind() { return SourceKind.EVENT; }

    @Override
    public boolean supportsSuggest() { return false; }

    @Override
    public PrincipalResult<List<Offer>> search(EventQuery q) {
        long start = System.currentTimeMillis();
        if (!guard.isCallable(sourceId())) {
            return PrincipalResult.skipped(sourceId(), "source disabled");
        }
        try {
            List<Event> events = service.getAll();
            List<Offer> offers = new ArrayList<>(TicketProOfferMapper.map(events));
            long duration = System.currentTimeMillis() - start;
            metrics.record(sourceId(), "OK", duration);
            return PrincipalResult.ok(sourceId(), offers, duration);
        } catch (Exception e) {
            return AtlasGateway.failure(sourceId(), e, start);
        }
    }
}
