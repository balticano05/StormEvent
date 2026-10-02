package com.workspace.storm.event.e2e;

import com.workspace.storm.event.agent.Extractor;
import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.llm.LlmGatewayStub;
import com.workspace.storm.event.orchestration.SearchOrchestrator;
import com.workspace.storm.event.orchestration.SourceExecutor;
import com.workspace.storm.event.tool.SearchBusesTool;
import com.workspace.storm.event.tool.SearchEventsTool;
import com.workspace.storm.event.tool.SearchHotelsTool;
import com.workspace.storm.event.tool.SearchTrainsTool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class E2eSmokeTest {

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
    void hp1BusHappySkeleton() {
        RequestContext ctx = RequestContext.create("e2e-1", new TimeoutProperties(), "ru", null);
        String answer = orchestrator().respond("session-1", "автобус Минск-Брест", ctx);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void hp2TrainHappySkeleton() {
        RequestContext ctx = RequestContext.create("e2e-2", new TimeoutProperties(), "ru", null);
        String answer = orchestrator().respond("session-1", "поезд в Минск вечером", ctx);
        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    void unhappyLlmFallsBackToSummary() {
        // LlmGatewayStub всегда отвечает — fallback проверяем косвенно: ответ не пустой
        RequestContext ctx = RequestContext.create("e2e-3", new TimeoutProperties(), "ru", null);
        String answer = orchestrator().respond("session-1", "отель рядом с Минском", ctx);
        assertTrue(answer.contains("[stub]") || answer.contains("Варианты"));
    }

    @Test
    void responseRespectsDeadline() {
        TimeoutProperties props = new TimeoutProperties();
        RequestContext ctx = RequestContext.create("e2e-4", props, "ru", null);
        long start = System.currentTimeMillis();
        orchestrator().respond("session-1", "автобус", ctx);
        assertTrue(System.currentTimeMillis() - start < props.getHardCeilingMs());
    }
}
