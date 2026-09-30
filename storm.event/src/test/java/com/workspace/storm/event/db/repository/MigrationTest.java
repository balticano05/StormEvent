package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class MigrationTest extends PostgresTestSupport {

    @Test
    void allMigrationsAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM storm.flyway_schema_history "
                        + "WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertEquals(List.of("1", "2", "3"), versions);
    }

    @Test
    void noFailedMigration() {
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM storm.flyway_schema_history WHERE NOT success", Integer.class);

        assertNotNull(failed);
        assertEquals(0, failed);
    }

    @Test
    void everyTableExists() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'storm' ORDER BY table_name",
                String.class);

        assertTrue(tables.containsAll(List.of(
                "cached_result", "idempotency", "request_log", "request_log_default",
                "session", "session_message", "source_error_log", "source_error_log_default",
                "source_state", "stats_source_hourly", "stats_source_hourly_default")),
                "missing tables: " + tables);
    }

    @Test
    void logTablesArePartitioned() {
        List<String> partitioned = jdbc.queryForList(
                """
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'storm' AND c.relkind = 'p'
                ORDER BY c.relname
                """,
                String.class);

        assertEquals(List.of("request_log", "source_error_log", "stats_source_hourly"), partitioned);
    }

    @Test
    void identifierColumnsUseCCollation() {
        String collation = jdbc.queryForObject(
                "SELECT collname FROM pg_collation c JOIN pg_attribute a ON a.attcollation = c.oid "
                        + "JOIN pg_class t ON t.oid = a.attrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                        + "WHERE n.nspname = 'storm' AND t.relname = 'source_state' AND a.attname = 'source'",
                String.class);

        assertEquals("C", collation);
    }

    @Test
    void sessionExpiryColumnIsIndexed() {
        String indexDef = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'storm' AND indexname = 'idx_session_expires_at'",
                String.class);

        assertTrue(indexDef.contains("expires_at"), indexDef);
    }
}
