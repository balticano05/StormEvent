package com.workspace.storm.event.web;

import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.db.repository.IdempotencyRepository;
import com.workspace.storm.event.db.repository.SessionRepository;
import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.orchestration.SearchOrchestrator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final SearchOrchestrator orchestrator;
    private final TimeoutProperties timeoutProperties;
    private final SessionRepository sessionRepository;
    private final IdempotencyRepository idempotencyRepository;

    public AgentController(SearchOrchestrator orchestrator, TimeoutProperties timeoutProperties,
                           SessionRepository sessionRepository, IdempotencyRepository idempotencyRepository) {
        this.orchestrator = orchestrator;
        this.timeoutProperties = timeoutProperties;
        this.sessionRepository = sessionRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String text, String sessionId) {
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> chat(@Valid @RequestBody ChatRequest request,
                                       @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader) {
        String requestId = requestIdHeader != null ? requestIdHeader : UUID.randomUUID().toString();
        RequestContext ctx = RequestContext.create(requestId, timeoutProperties, "ru", null);

        var cached = idempotencyRepository.get(UUID.fromString(requestId));
        if (cached.isPresent()) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(cached.get().getResponseJson());
        }

        UUID sessionId;
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            SessionEntity session = new SessionEntity();
            session.setId(UUID.randomUUID());
            session.setState("NEW");
            session.setCreatedAt(Instant.now());
            session.setLastAccessAt(Instant.now());
            session.setExpiresAt(Instant.now().plusSeconds(900));
            session.setTtlSeconds(900);
            sessionRepository.insert(session);
            sessionId = session.getId();
        } else {
            sessionId = UUID.fromString(request.sessionId());
            sessionRepository.touch(sessionId);
        }

        String answer = orchestrator.respond(sessionId.toString(), request.text(), ctx);

        IdempotencyEntity idempotency = new IdempotencyEntity();
        idempotency.setRequestId(UUID.fromString(requestId));
        idempotency.setResponseJson(answer);
        idempotency.setSessionId(sessionId);
        idempotency.setCreatedAt(Instant.now());
        idempotency.setExpiresAt(Instant.now().plusSeconds(300));
        idempotencyRepository.putIfAbsent(idempotency);

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(answer);
    }
}
