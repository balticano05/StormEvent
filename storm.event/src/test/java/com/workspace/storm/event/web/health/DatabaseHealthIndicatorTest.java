package com.workspace.storm.event.web.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseHealthIndicatorTest {

    @Test
    void healthyWhenProbeAnswers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertTrue(new DatabaseHealthIndicator(jdbc).isHealthy());
    }

    @Test
    void notHealthyWhenDatabaseIsDown() {
        assertFalse(downDatabase().isHealthy());
    }

    @Test
    void catalogStatsFallBackToZeroWhenDatabaseIsDown() {
        assertEquals(Map.of("db.tables.count", 0L, "db.indexes.count", 0L), downDatabase().catalogStats());
    }

    @Test
    void readinessAnswersNotReadyWhenDatabaseIsDown() {
        HealthController controller = new HealthController(downDatabase());

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("NOT READY", response.getBody().get("status"));
    }

    @Test
    void livenessStaysUpWhenDatabaseIsDown() {
        HealthController controller = new HealthController(downDatabase());

        ResponseEntity<String> response = controller.liveness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody());
    }

    private DatabaseHealthIndicator downDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new CannotGetJdbcConnectionException("db down"));
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new CannotGetJdbcConnectionException("db down"));
        return new DatabaseHealthIndicator(jdbc);
    }
}
