package com.workspace.storm.event.tool;

import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.ToolResult;
import com.workspace.storm.event.gateway.EventGateway;
import com.workspace.storm.event.gateway.EventQuery;
import com.workspace.storm.event.orchestration.ResultCollector;
import com.workspace.storm.event.orchestration.SourceCall;
import com.workspace.storm.event.orchestration.SourceExecutor;

import java.util.ArrayList;
import java.util.List;

public class SearchEventsTool {

    private final List<EventGateway> gateways;
    private final SourceExecutor executor;

    public SearchEventsTool(List<EventGateway> gateways, SourceExecutor executor) {
        this.gateways = List.copyOf(gateways);
        this.executor = executor;
    }

    public ToolResult search(EventQuery q, RequestContext ctx) {
        long start = System.currentTimeMillis();
        List<SourceCall<List<Offer>>> calls = new ArrayList<>();
        for (EventGateway gateway : gateways) {
            calls.add(new SourceCall<>(gateway.sourceId(), () -> gateway.search(q)));
        }
        List<PrincipalResult<List<Offer>>> results = executor.execute(calls, ctx);
        return ResultCollector.collect("EVENT", results, System.currentTimeMillis() - start);
    }
}
