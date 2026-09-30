package com.workspace.storm.event.web.health;

import com.workspace.storm.event.db.support.DbMigrationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DatabaseHealthIndicator dbHealth;
    private final DbMigrationStatus migrationStatus;

    @GetMapping("/api/v1/health/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("OK");
    }

    /**
     * Readiness: соединение живое и миграции применены (шаг 369). Ответ 503
     * с причиной - иначе потерявшийся из-за рассинхрона схемы инстанс месяцами
     * остаётся в балансировщике.
     */
    @GetMapping(value = "/api/v1/health/ready", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> readiness() {
        if (!dbHealth.isHealthy()) {
            return ResponseEntity.status(503).body(Map.of("status", "NOT READY", "reason", "db unavailable"));
        }
        DbMigrationStatus.MigrationSnapshot migrations = migrationStatus.snapshot();
        if (!migrations.ready()) {
            // Причина именно в миграциях, а не в соединении: isHealthy() выше
            // уже подтвердил, что с БД можно говорить, значит не хватает схемы.
            return ResponseEntity.status(503).body(Map.of(
                    "status", "NOT READY",
                    "reason", "migrations not applied",
                    "applied", migrations.applied(),
                    "expected", migrations.expected()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("db", dbHealth.catalogStats());
        body.put("db.migrations", migrations);
        return ResponseEntity.ok(body);
    }
}
