package com.workspace.storm.event.gateway;

import com.workspace.storm.event.dto.AtlasSearchRequest;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import com.workspace.storm.event.entity.atlas.AtlasStation;
import com.workspace.storm.event.normalize.source.AtlasOfferMapper;
import com.workspace.storm.event.service.AtlasService;

import java.util.ArrayList;
import java.util.List;

public class AtlasGateway implements TransportGateway {

    private final AtlasService service;
    private final SourceStateGuard guard;
    private final SourceMetrics metrics;

    public AtlasGateway(AtlasService service, SourceStateGuard guard, SourceMetrics metrics) {
        this.service = service;
        this.guard = guard;
        this.metrics = metrics;
    }

    @Override
    public String sourceId() { return "atlasbus"; }

    @Override
    public SourceKind kind() { return SourceKind.BUS; }

    @Override
    public boolean supportsSuggest() { return true; }

    @Override
    public PrincipalResult<List<Offer>> search(TransportQuery q) {
        long start = System.currentTimeMillis();
        if (!guard.isCallable(sourceId())) {
            return skipped(sourceId());
        }
        try {
            AtlasSearchRequest req = new AtlasSearchRequest();
            resolve(req, q);
            AtlasSearchResult result = service.search(req);
            List<Offer> offers = asOffers(AtlasOfferMapper.map(result));
            return finish(sourceId(), offers, result, start);
        } catch (Exception e) {
            return failure(sourceId(), e, start);
        }
    }

    private void resolve(AtlasSearchRequest req, TransportQuery q) {
        List<AtlasStation> from = service.findStations(q.from());
        List<AtlasStation> to = service.findStations(q.to());
        req.setFromId(from.isEmpty() ? q.from() : from.get(0).getId());
        req.setToId(to.isEmpty() ? q.to() : to.get(0).getId());
        req.setDate(q.date());
        req.setPassengers(q.passengers());
    }

    private static List<Offer> asOffers(List<com.workspace.storm.event.dto.offer.TransportOffer> t) {
        return new ArrayList<>(t);
    }

    private PrincipalResult<List<Offer>> finish(String sourceId, List<Offer> offers, AtlasSearchResult result, long start) {
        long duration = System.currentTimeMillis() - start;
        if (AtlasOfferMapper.isPartial(result)) {
            metrics.record(sourceId, "PARTIAL", duration);
            return PrincipalResult.partial(sourceId, offers, List.of(), duration);
        }
        metrics.record(sourceId, "OK", duration);
        return PrincipalResult.ok(sourceId, offers, duration);
    }

    @Override
    public PrincipalResult<List<StationSuggestion>> suggest(String query) {
        try {
            List<AtlasStation> stations = service.findStations(query);
            List<StationSuggestion> out = new ArrayList<>();
            for (AtlasStation s : stations) {
                out.add(new StationSuggestion(sourceId(), s.getName(), s.getId(), "station"));
            }
            return PrincipalResult.ok(sourceId(), out, 0L);
        } catch (Exception e) {
            return failure(sourceId(), e, System.currentTimeMillis());
        }
    }

    static <T> PrincipalResult<T> failure(String sourceId, Exception e, long start) {
        long duration = System.currentTimeMillis() - start;
        String msg = e instanceof java.util.concurrent.TimeoutException || e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")
                ? "timeout" : e.getMessage();
        if ("timeout".equals(msg)) {
            return PrincipalResult.timeout(sourceId, duration);
        }
        return PrincipalResult.failure(sourceId, msg == null ? e.getClass().getSimpleName() : msg, duration);
    }

    static <T> PrincipalResult<T> skipped(String sourceId) {
        return PrincipalResult.skipped(sourceId, "source disabled");
    }

    static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
