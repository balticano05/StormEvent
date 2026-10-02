package com.workspace.storm.event.gateway;

import com.workspace.storm.event.client.BelHotelClient;
import com.workspace.storm.event.dto.HotelSearchRequest;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.entity.hotel.Hotel;
import com.workspace.storm.event.normalize.source.BelHotelOfferMapper;

import java.util.ArrayList;
import java.util.List;

public class BelHotelGateway implements HotelGateway {

    private final BelHotelClient client;
    private final SourceStateGuard guard;
    private final SourceMetrics metrics;

    public BelHotelGateway(BelHotelClient client, SourceStateGuard guard, SourceMetrics metrics) {
        this.client = client;
        this.guard = guard;
        this.metrics = metrics;
    }

    @Override
    public String sourceId() { return "belhotel"; }

    @Override
    public SourceKind kind() { return SourceKind.HOTEL; }

    @Override
    public boolean supportsSuggest() { return false; }

    @Override
    public PrincipalResult<List<Offer>> search(HotelQuery q) {
        long start = System.currentTimeMillis();
        if (!guard.isCallable(sourceId())) {
            return PrincipalResult.skipped(sourceId(), "source disabled");
        }
        try {
            HotelSearchRequest req = new HotelSearchRequest();
            req.setCheckIn(q.checkIn());
            req.setCheckOut(q.checkOut());
            req.setAdults(q.adults());
            List<Hotel> hotels = client.search(req);
            List<Offer> offers = new ArrayList<>(BelHotelOfferMapper.map(hotels));
            long duration = System.currentTimeMillis() - start;
            metrics.record(sourceId(), "OK", duration);
            return PrincipalResult.ok(sourceId(), offers, duration);
        } catch (Exception e) {
            return AtlasGateway.failure(sourceId(), e, start);
        }
    }
}
