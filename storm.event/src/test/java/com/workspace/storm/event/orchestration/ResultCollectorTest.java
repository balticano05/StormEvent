package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.ToolResult;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class ResultCollectorTest {

    @Test
    void aggregatesOffersAndWarnings() {
        PrincipalResult<List<Offer>> ok = PrincipalResult.ok("atlasbus", List.of(), 5L);
        PrincipalResult<List<Offer>> failed = PrincipalResult.failure("bzd", "403", 5L);
        ToolResult result = ResultCollector.collect("transport", List.of(ok, failed), 20L);
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("bzd"));
        assertEquals(2, result.sourceResults().size());
    }

    @Test
    void dedupesWarnings() {
        PrincipalResult<List<Offer>> f1 = PrincipalResult.failure("bzd", "500", 5L);
        PrincipalResult<List<Offer>> f2 = PrincipalResult.failure("bzd", "500", 5L);
        ToolResult result = ResultCollector.collect("transport", List.of(f1, f2), 20L);
        assertEquals(1, result.warnings().size());
    }
}
