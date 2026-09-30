package com.workspace.storm.event.web.health;

import com.workspace.storm.event.db.support.DbMigrationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator {

    private static final String TABLE_COUNT = """
            SELECT count(*) FROM information_schema.tables
            WHERE table_schema = 'storm' AND table_type = 'BASE TABLE'
              AND table_name NOT LIKE 'flyway%'
            """;

    private static final String INDEX_COUNT = """
            SELECT count(*) FROM pg_indexes
            WHERE schemaname = 'storm' AND tablename NOT LIKE 'flyway%'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DbMigrationStatus migrationStatus;

    public boolean isHealthy() {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isPresent();
        } catch (DataAccessException e) {
            return false;
        }
    }

    /**
 * Готовность принимать трафик: соединение живое и схема актуальна.
 *
 * <p>Приложение на старой схеме живое, но обслуживать запросы не должно.
 * Метод нужен сам по себе — {@link HealthController} различает недоступную БД
 * и старую схему и потому собирает эти две проверки у себя, чтобы отдать в
 * теле ответа конкретную причину. Дублирование тут осознанное: один флаг
 * «не готов» не объясняет оператору, что именно чинить.
 */
    public boolean isReady() {
        if (!isHealthy()) {
            return false;
        }
        try {
            return migrationStatus.snapshot().ready();
        } catch (DataAccessException e) {
            return false;
        }
    }

    public long tableCount() {
        return safeCount(TABLE_COUNT);
    }

    public long indexCount() {
        return safeCount(INDEX_COUNT);
    }

    public Map<String, Long> catalogStats() {
        return Map.of(
                "db.tables.count", tableCount(),
                "db.indexes.count", indexCount());
    }

    private long safeCount(String sql) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class)).orElse(0L);
        } catch (DataAccessException e) {
            return 0L;
        }
    }
}
