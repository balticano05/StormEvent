package com.workspace.storm.event.web.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DatabaseHealthIndicator dbHealth;

    @GetMapping("/api/v1/health/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/api/v1/health/ready")
    public ResponseEntity<String> readiness() {
        return dbHealth.isHealthy()
                ? ResponseEntity.ok("OK")
                : ResponseEntity.status(503).body("NOT READY");
    }
}