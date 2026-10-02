package com.workspace.storm.event.tool;

import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.ToolResult;
import com.workspace.storm.event.gateway.SourceKind;
import com.workspace.storm.event.gateway.TransportGateway;
import com.workspace.storm.event.gateway.TransportQuery;
import com.workspace.storm.event.orchestration.ResultCollector;
import com.workspace.storm.event.orchestration.SourceCall;
import com.workspace.storm.event.orchestration.SourceExecutor;

import java.util.ArrayList;
import java.util.List;

public class SearchTrainsTool {

    private final List<TransportGateway> gateways;
    private final SourceExecutor executor;

    public SearchTrainsTool(List<TransportGateway> gateways, SourceExecutor executor) {
        this.gateways = gateways.stream().filter(g -> g.kind() == SourceKind.TRAIN).toList();
        this.executor = executor;
    }

    public ToolResult search(TransportQuery q, RequestContext ctx) {
        long start = System.currentTimeMillis();
        List<SourceCall<List<Offer>>> calls = new ArrayList<>();
        for (TransportGateway gateway : gateways) {
            calls.add(new SourceCall<>(gateway.sourceId(), () -> gateway.search(q)));
        }
        List<PrincipalResult<List<Offer>>> results = executor.execute(calls, ctx);
        return ResultCollector.collect("TRAIN", results, System.currentTimeMillis() - start);
    }
}
