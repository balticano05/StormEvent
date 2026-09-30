package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class SchemaConstraintTest extends PostgresTestSupport {

    private static final List<String> TABLES = List.of(
            "session", "session_message", "source_state", "cached_result",
            "idempotency", "request_log", "source_error_log", "stats_source_hourly");

    @Test
    void everyForeignKeyHasSupportingIndex() {
        List<Map<String, Object>> keys = jdbc.queryForList("""
            SELECT c.conrelid::regclass::text AS table_name,
                   a.attname AS column_name
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
            WHERE c.contype = 'f' AND c.connamespace = 'storm'::regnamespace
            """);

        assertFalse(keys.isEmpty(), "в схеме должны быть внешние ключи");
        for (Map<String, Object> key : keys) {
            String table = String.valueOf(key.get("table_name"));
            String column = String.valueOf(key.get("column_name"));
            assertTrue(hasIndexOnColumn(table, column),
                    "на внешний ключ " + table + "." + column + " нужен индекс с этой колонкой первой");
        }
    }

    @Test
    void sessionScopedTablesReferenceSession() {
        assertTrue(hasForeignKey("session_message", "session_id", "CASCADE"),
                "реплики диалога обязаны удаляться вместе с сессией");
        assertTrue(hasForeignKey("request_log", "session_id", "SET NULL"),
                "аудит переживает удаление сессии, ссылка обнуляется");
    }

    @Test
    void auditTablesKeepHistoryWithoutForeignKey() {
        assertFalse(hasAnyForeignKey("source_error_log"),
                "журнал ошибок источников переживает сессии и не связан FK с request_log");
    }

    @Test
    void partitionedTablesKeepPartitionKeyInPrimaryKey() {
        List<Map<String, Object>> tables = jdbc.queryForList("""
            SELECT c.relname AS name
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'storm' AND c.relkind = 'p'
            """);

        assertEquals(3, tables.size(), "партиционированы журналы запросов, ошибок и агрегаты");
        for (Map<String, Object> table : tables) {
            String name = String.valueOf(table.get("name"));
            String partitionKey = name.equals("stats_source_hourly") ? "hour" : "created_at";
            List<String> pkColumns = jdbc.queryForList("""
                SELECT a.attname AS name
                FROM pg_index i
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                WHERE i.indrelid = to_regclass('storm.' || ?) AND i.indisprimary
                """, String.class, name);
            assertTrue(pkColumns.contains(partitionKey),
                    "PK " + name + " обязан включать ключ партициционирования " + partitionKey + ", а не " + pkColumns);
        }
    }

    @Test
    void everyPartitionedTableHasDefaultPartition() {
        for (String table : List.of("request_log", "source_error_log", "stats_source_hourly")) {
            Boolean hasDefault = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_class child
                    JOIN pg_inherits i ON i.inhrelid = child.oid
                    WHERE child.relname = ? AND child.relispartition
                      AND pg_get_expr(child.relpartbound, child.oid) = 'DEFAULT'
                )
                """, Boolean.class, table + "_default");
            assertTrue(Boolean.TRUE.equals(hasDefault), "у " + table + " должна быть DEFAULT-партиция");
        }
    }

    @Test
    void allSchemaTablesExist() {
        List<String> found = domainTables();

        assertTrue(found.containsAll(TABLES), "нет таблиц: " + TABLES.stream().filter(t -> !found.contains(t)).toList());
    }

    @Test
    void checkConstraintsArePresent() {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            WHERE c.contype = 'c' AND c.connamespace = 'storm'::regnamespace
              AND t.relname <> 'flyway_schema_history'
            """, Integer.class);

        assertTrue(count != null && count >= 8, "нужны CHECK-ограничения на role, kind, state, status и count");
    }

    @Test
    void timestampsAreTimezoneAware() {
        List<String> naive = jdbc.queryForList("""
            SELECT c.relname || '.' || a.attname AS ref
            FROM pg_attribute a
            JOIN pg_class c ON c.oid = a.attrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'storm' AND c.relkind IN ('r', 'p')
              AND c.relname <> 'flyway_schema_history'
              AND a.atttypid = 'timestamp'::regtype AND a.attnum > 0 AND NOT a.attisdropped
            """, String.class);

        assertTrue(naive.isEmpty(), "технические метки должны быть timestamptz (ADR-016), найдено: " + naive);
    }

    @Test
    void identifiersUseCCollation() {
        Integer cCollations = jdbc.queryForObject("""
            SELECT COUNT(*) FROM pg_attribute a
            JOIN pg_class c ON c.oid = a.attrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'storm' AND a.attcollation <> 0
              AND a.attname IN ('source', 'domain', 'code')
            """, Integer.class);

        assertTrue(cCollations != null && cCollations > 0, "идентификаторы источников сравниваются в COLLATE \"C\"");
    }

    private boolean hasIndexOnColumn(String table, String column) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM pg_index i
            JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
            WHERE i.indrelid = to_regclass(?) AND a.attname = ?
            """, Integer.class, "storm." + table, column);
        return count != null && count > 0;
    }

    private List<String> domainTables() {
        return jdbc.queryForList("""
            SELECT c.relname FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'storm' AND c.relkind IN ('r', 'p')
              AND c.relname <> 'flyway_schema_history'
            ORDER BY c.relname
            """, String.class);
    }

    private boolean hasForeignKey(String table, String column, String onDelete) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
            WHERE c.contype = 'f' AND c.connamespace = 'storm'::regnamespace
              AND c.conrelid = to_regclass(?) AND a.attname = ?
              AND c.confdeltype = ?::char
            """, Integer.class, "storm." + table, column, onDeleteCode(onDelete));
        return count != null && count > 0;
    }

    private boolean hasAnyForeignKey(String table) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM pg_constraint
            WHERE contype = 'f' AND conrelid = to_regclass(?)
            """, Integer.class, "storm." + table);
        return count != null && count > 0;
    }

    private static String onDeleteCode(String action) {
        return switch (action) {
            case "CASCADE" -> "c";
            case "SET NULL" -> "n";
            case "RESTRICT" -> "r";
            default -> throw new IllegalArgumentException("неизвестное действие: " + action);
        };
    }
}
