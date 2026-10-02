package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.context.RequestContext;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SearchOrchestratorTest {

    @Test
    void emptyPlanReturnsEmptyResult() {
        TimeoutProperties props = new TimeoutProperties();
        RequestContext ctx = RequestContext.create("r1", props, "ru", null);
        SearchOrchestrator orch = new SearchOrchestrator();
        assertTrue(orch.run(new PipelineContext("s1", ctx), RunPlan.of()).offers().isEmpty());
    }
}
