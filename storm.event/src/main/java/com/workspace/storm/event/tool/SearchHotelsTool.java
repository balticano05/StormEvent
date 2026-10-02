package com.workspace.storm.event.tool;

import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.ToolResult;
import com.workspace.storm.event.gateway.HotelGateway;
import com.workspace.storm.event.gateway.HotelQuery;
import com.workspace.storm.event.orchestration.ResultCollector;
import com.workspace.storm.event.orchestration.SourceCall;
import com.workspace.storm.event.orchestration.SourceExecutor;

import java.util.ArrayList;
import java.util.List;

public class SearchHotelsTool {

    private final List<HotelGateway> gateways;
    private final SourceExecutor executor;

    public SearchHotelsTool(List<HotelGateway> gateways, SourceExecutor executor) {
        this.gateways = List.copyOf(gateways);
        this.executor = executor;
    }

    public ToolResult search(HotelQuery q, RequestContext ctx) {
        long start = System.currentTimeMillis();
        List<SourceCall<List<Offer>>> calls = new ArrayList<>();
        for (HotelGateway gateway : gateways) {
            calls.add(new SourceCall<>(gateway.sourceId(), () -> gateway.search(q)));
        }
        List<PrincipalResult<List<Offer>>> results = executor.execute(calls, ctx);
        return ResultCollector.collect("HOTEL", results, System.currentTimeMillis() - start);
    }
}
