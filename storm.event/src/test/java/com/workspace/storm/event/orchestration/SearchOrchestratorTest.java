package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.agent.Extractor;
import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.llm.LlmGatewayStub;
import com.workspace.storm.event.tool.SearchBusesTool;
import com.workspace.storm.event.tool.SearchEventsTool;
import com.workspace.storm.event.tool.SearchHotelsTool;
import com.workspace.storm.event.tool.SearchTrainsTool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class SearchOrchestratorTest {

    private SearchOrchestrator orchestrator() {
        return new SearchOrchestrator(
                new Extractor(),
                new SearchBusesTool(List.of(), new SourceExecutor()),
                new SearchTrainsTool(List.of(), new SourceExecutor()),
                new SearchEventsTool(List.of(), new SourceExecutor()),
                new SearchHotelsTool(List.of(), new SourceExecutor()),
                new LlmGatewayStub());
    }

    @Test
    void emptyPlanReturnsEmptyResult() {
        TimeoutProperties props = new TimeoutProperties();
        RequestContext ctx = RequestContext.create("r1", props, "ru", null);
        SearchOrchestrator orch = orchestrator();
        assertTrue(orch.run(new PipelineContext("s1", ctx), RunPlan.of()).offers().isEmpty());
    }

    @Test
    void respondReturnsTextWithNoOffers() {
        TimeoutProperties props = new TimeoutProperties();
        RequestContext ctx = RequestContext.create("r1", props, "ru", null);
        String answer = orchestrator().respond("s1", "автобус Минск-Гродно", ctx);
        assertNotNull(answer);
        assertTrue(answer.contains("Варианты не найдены."));
    }
}
