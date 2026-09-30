package com.workspace.storm.event.web.health;

import com.workspace.storm.event.config.DbProperties;
import com.workspace.storm.event.db.support.DbMigrationStatus;
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

        assertTrue(new DatabaseHealthIndicator(jdbc, migrationStatus(4, 4)).isHealthy());
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
        HealthController controller = new HealthController(downDatabase(), migrationStatus(4, 4));

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("NOT READY", response.getBody().get("status"));
    }

    @Test
    void livenessStaysUpWhenDatabaseIsDown() {
        HealthController controller = new HealthController(downDatabase(), migrationStatus(4, 4));

        ResponseEntity<String> response = controller.liveness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody());
    }

    @Test
    void readyOnlyWhenMigrationsApplied() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertTrue(new DatabaseHealthIndicator(jdbc, migrationStatus(4, 4)).isReady(),
                "схема актуальна - можно принимать трафик");
        assertFalse(new DatabaseHealthIndicator(jdbc, migrationStatus(3, 4)).isReady(),
                "приложение на старой схеме не готово");
    }

    @Test
    void readinessReportsMissingMigrations() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        HealthController controller = new HealthController(
                new DatabaseHealthIndicator(jdbc, migrationStatus(3, 4)), migrationStatus(3, 4));

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("migrations not applied", response.getBody().get("reason"));
        assertEquals(3, response.getBody().get("applied"));
        assertEquals(4, response.getBody().get("expected"));
    }

    @Test
    void readinessReportsOkWhenMigrationsApplied() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(11L);
        HealthController controller = new HealthController(
                new DatabaseHealthIndicator(jdbc, migrationStatus(4, 4)), migrationStatus(4, 4));

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody().get("status"));
        assertTrue(response.getBody().containsKey("db.migrations"));
    }

    private DatabaseHealthIndicator downDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new CannotGetJdbcConnectionException("db down"));
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new CannotGetJdbcConnectionException("db down"));
        return new DatabaseHealthIndicator(jdbc, migrationStatus(4, 4));
    }

    private DbMigrationStatus migrationStatus(int applied, int expected) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(applied);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("4");
        DbProperties properties = new DbProperties();
        properties.setExpectedMigrations(expected);
        return new DbMigrationStatus(jdbc, properties);
    }
}
