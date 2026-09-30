package com.workspace.storm.event.web.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DatabaseHealthIndicator dbHealth;

    @GetMapping("/api/v1/health/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/api/v1/health/ready", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> readiness() {
        if (!dbHealth.isHealthy()) {
            return ResponseEntity.status(503).body(Map.of("status", "NOT READY"));
        }
        return ResponseEntity.ok(Map.of("status", "OK", "db", dbHealth.catalogStats()));
    }
}
