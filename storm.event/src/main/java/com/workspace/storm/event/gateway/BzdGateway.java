package com.workspace.storm.event.gateway;

import com.workspace.storm.event.client.BzdClient;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.entity.bzd.BzdStation;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import com.workspace.storm.event.normalize.source.BzdOfferMapper;

import java.util.ArrayList;
import java.util.List;

public class BzdGateway implements TransportGateway {

    private final BzdClient client;
    private final SourceStateGuard guard;
    private final SourceMetrics metrics;

    public BzdGateway(BzdClient client, SourceStateGuard guard, SourceMetrics metrics) {
        this.client = client;
        this.guard = guard;
        this.metrics = metrics;
    }

    @Override
    public String sourceId() { return "bzd"; }

    @Override
    public SourceKind kind() { return SourceKind.TRAIN; }

    @Override
    public boolean supportsSuggest() { return true; }

    @Override
    public PrincipalResult<List<Offer>> search(TransportQuery q) {
        long start = System.currentTimeMillis();
        if (!guard.isCallable(sourceId())) {
            return PrincipalResult.skipped(sourceId(), "source disabled");
        }
        try {
            List<BzdTrain> trains = client.searchRoute(q.from(), q.to(), q.date());
            List<Offer> offers = new ArrayList<>(BzdOfferMapper.map(trains));
            long duration = System.currentTimeMillis() - start;
            metrics.record(sourceId(), "OK", duration);
            return PrincipalResult.ok(sourceId(), offers, duration);
        } catch (Exception e) {
            return AtlasGateway.failure(sourceId(), e, start);
        }
    }

    @Override
    public PrincipalResult<List<StationSuggestion>> suggest(String query) {
        try {
            List<BzdStation> stations = client.resolveStations(query);
            List<StationSuggestion> out = new ArrayList<>();
            for (BzdStation s : stations) {
                out.add(new StationSuggestion(sourceId(), s.getLabel(), s.getValue() != null ? s.getValue() : s.getLabel(), "station"));
            }
            return PrincipalResult.ok(sourceId(), out, 0L);
        } catch (Exception e) {
            return AtlasGateway.failure(sourceId(), e, System.currentTimeMillis());
        }
    }
}
