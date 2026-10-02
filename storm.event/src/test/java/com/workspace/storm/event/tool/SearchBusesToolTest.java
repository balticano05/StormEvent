package com.workspace.storm.event.tool;

import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.ToolResult;
import com.workspace.storm.event.gateway.SourceKind;
import com.workspace.storm.event.gateway.StationSuggestion;
import com.workspace.storm.event.gateway.TransportGateway;
import com.workspace.storm.event.gateway.TransportQuery;
import com.workspace.storm.event.orchestration.SourceExecutor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class SearchBusesToolTest {

    private static TransportGateway busGateway(String id, PrincipalResult<List<Offer>> result) {
        return new TransportGateway() {
            @Override public String sourceId() { return id; }
            @Override public SourceKind kind() { return SourceKind.BUS; }
            @Override public boolean supportsSuggest() { return true; }
            @Override public PrincipalResult<List<Offer>> search(TransportQuery q) { return result; }
            @Override public PrincipalResult<List<StationSuggestion>> suggest(String query) { return PrincipalResult.ok(id, List.of(), 0L); }
        };
    }

    @Test
    void mergesTwoSources() {
        List<TransportGateway> gateways = List.of(
                busGateway("atlasbus", PrincipalResult.ok("atlasbus", List.of(), 1L)),
                busGateway("ticketbus", PrincipalResult.ok("ticketbus", List.of(), 1L)));
        SearchBusesTool tool = new SearchBusesTool(gateways, new SourceExecutor());
        ToolResult result = tool.search(new TransportQuery("Минск", "Гродно", LocalDate.now().plusDays(1), 1, null, false, false),
                RequestContext.create("r", new TimeoutProperties(), "ru", null));
        assertEquals(2, result.sourceResults().size());
        assertEquals("BUS", result.domain());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void failedSourceProducesWarning() {
        List<TransportGateway> gateways = List.of(
                busGateway("atlasbus", PrincipalResult.failure("atlasbus", "500", 1L)),
                busGateway("ticketbus", PrincipalResult.ok("ticketbus", List.of(), 1L)));
        SearchBusesTool tool = new SearchBusesTool(gateways, new SourceExecutor());
        ToolResult result = tool.search(new TransportQuery("Минск", "Гродно", LocalDate.now().plusDays(1), 1, null, false, false),
                RequestContext.create("r", new TimeoutProperties(), "ru", null));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("atlasbus"));
    }
}
