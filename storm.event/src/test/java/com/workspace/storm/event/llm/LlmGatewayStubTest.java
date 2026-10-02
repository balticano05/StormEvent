package com.workspace.storm.event.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class LlmGatewayStubTest {

    @Test
    void stubEchoesLastHistoryMessage() {
        LlmGatewayStub stub = new LlmGatewayStub();
        LlmResponse response = stub.complete(new LlmRequest("sys", List.of("привет"), List.of(), 1000L));
        assertFalse(response.failed());
        assertTrue(response.text().contains("привет"));
        assertTrue(response.toolCalls().isEmpty());
    }

    @Test
    void emptyHistoryProducesStubOnly() {
        LlmGatewayStub stub = new LlmGatewayStub();
        LlmResponse response = stub.complete(new LlmRequest("sys", List.of(), List.of(), 1000L));
        assertTrue(response.text().startsWith(LlmGatewayStub.STUB_MARK));
    }
}
