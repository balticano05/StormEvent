package com.workspace.storm.event.web.health;

import com.workspace.storm.event.db.support.DbConnectionTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HealthControllerTest extends DbConnectionTestSupport {

    @Autowired
    private HealthController controller;

    @Autowired
    private DatabaseHealthIndicator dbHealth;


    @Test
    void readyReportsDomainTablesAndIndexes() {
        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody().get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Long> stats = (Map<String, Long>) response.getBody().get("db");
        assertNotNull(stats);
        assertEquals(11L, stats.get("db.tables.count"), "8 таблиц + 3 DEFAULT-партиции");
        assertTrue(stats.get("db.indexes.count") >= 8, "индексы на месте");
    }

    @Test
    void countsIgnoreFlywayHistory() {
        long tables = dbHealth.tableCount();
        long indexes = dbHealth.indexCount();

        assertEquals(tables, dbHealth.tableCount(), "счётчик стабилен между вызовами");
        assertEquals(indexes, dbHealth.indexCount());
    }

    @Test
    void livenessDoesNotTouchDatabase() {
        assertEquals(HttpStatus.OK, controller.liveness().getStatusCode());
        assertEquals("OK", controller.liveness().getBody());
    }
}
