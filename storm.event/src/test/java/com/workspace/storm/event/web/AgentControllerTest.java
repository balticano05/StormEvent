package com.workspace.storm.event.web;

import com.workspace.storm.event.agent.Extractor;
import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.repository.IdempotencyRepository;
import com.workspace.storm.event.db.repository.SessionRepository;
import com.workspace.storm.event.llm.LlmGatewayStub;
import com.workspace.storm.event.orchestration.SearchOrchestrator;
import com.workspace.storm.event.orchestration.SourceExecutor;
import com.workspace.storm.event.tool.SearchBusesTool;
import com.workspace.storm.event.tool.SearchEventsTool;
import com.workspace.storm.event.tool.SearchHotelsTool;
import com.workspace.storm.event.tool.SearchTrainsTool;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentControllerTest {

    private MockMvc mvc() {
        SearchOrchestrator orchestrator = new SearchOrchestrator(
                new Extractor(),
                new SearchBusesTool(List.of(), new SourceExecutor()),
                new SearchTrainsTool(List.of(), new SourceExecutor()),
                new SearchEventsTool(List.of(), new SourceExecutor()),
                new SearchHotelsTool(List.of(), new SourceExecutor()),
                new LlmGatewayStub());
        SessionRepository sessionRepository = new SessionRepository(null) {
            @Override public void insert(SessionEntity entity) { }
            @Override public int touch(UUID id) { return 1; }
        };
        IdempotencyRepository idempotencyRepository = new IdempotencyRepository(null) {
            @Override public Optional<IdempotencyEntity> get(UUID requestId) { return Optional.empty(); }
            @Override public boolean putIfAbsent(IdempotencyEntity entity) { return true; }
        };
        return MockMvcBuilders.standaloneSetup(new AgentController(orchestrator, new TimeoutProperties(), sessionRepository, idempotencyRepository)).build();
    }

    @Test
    void chatReturnsPlainText() throws Exception {
        mvc().perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"автобус Минск-Гродно\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[stub]")));
    }

    @Test
    void tooLongTextIsRejected() throws Exception {
        String longText = "а".repeat(2001);
        mvc().perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + longText + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
