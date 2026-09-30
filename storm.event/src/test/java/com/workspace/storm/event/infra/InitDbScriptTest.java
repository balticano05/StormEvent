package com.workspace.storm.event.infra;

import com.workspace.storm.event.config.DbProperties;
import com.workspace.storm.event.db.support.DbMigrationStatus;
import com.workspace.storm.event.db.support.PgStatStatementsRepository;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Скрипт инициализации кластера: расширение и схема - до Flyway, таблицы -
 * после (ADR-044).
 */
@Tag("db")
class InitDbScriptTest extends PostgresTestSupport {

    private static final Path SCRIPT = Path.of("..", "infra", "initdb", "10-init-db.sql");

    /**
     * В контейнере тестов initdb не выполняется, поэтому применяем скрипт сами -
     * дважды за проход: повторный запуск не должен ломать окружение.
     */
    @BeforeEach
    void applyScriptTwice() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, script());
            ScriptUtils.executeSqlScript(connection, script());
        }
    }

    @Test
    void scriptIsAppliedWithoutErrors() {
        assertEquals("storm", jdbc.queryForObject(
                "SELECT nspname FROM pg_namespace WHERE nspname = 'storm'", String.class));
    }

    @Test
    void extensionIsInstalled() {
        assertEquals("1", jdbc.queryForObject("""
            SELECT count(*) FROM pg_extension WHERE extname = 'pg_stat_statements'
            """, String.class));
    }

    @Test
    void statisticsViewNeedsPreloadAndDegradesGracefully() {
        PgStatStatementsRepository repository = new PgStatStatementsRepository(jdbc);

        // В контейнере тестов расширение создано, но не в shared_preload_libraries:
        // выборка не работает и обязана деградировать, а не ронять диагностику.
        assertThrows(RuntimeException.class, () -> jdbc.queryForObject(
                "SELECT count(*) FROM pg_stat_statements", Long.class));
        assertFalse(repository.isAvailable());
        assertTrue(repository.topByTotalTime(5).isEmpty());
    }

    @Test
    void scriptCreatesNoTablesSoFlywayOwnsSchema() throws Exception {
        String sql = Files.readString(script().getFile().toPath());

        assertFalse(sql.contains("CREATE TABLE"), "таблицы создаёт Flyway");
        assertFalse(sql.contains("CREATE INDEX"), "индексы создаёт Flyway");
        assertFalse(sql.contains("ALTER TABLE"), "структуру правит Flyway");
        assertTrue(sql.contains("CREATE EXTENSION IF NOT EXISTS pg_stat_statements"));
        assertTrue(sql.contains("CREATE SCHEMA IF NOT EXISTS storm"));
    }

    @Test
    void scriptUsesOnlyPortableSqlAndNoSecrets() throws Exception {
        String sql = Files.readString(script().getFile().toPath());

        // Комментарии про эти команды есть, исполняемый SQL - нет.
        String executable = sql.lines()
                .map(line -> line.strip())
                .filter(line -> !line.startsWith("--"))
                .reduce("", (left, right) -> left + "\n" + right);
        for (String psqlMeta : List.of("\\connect", "\\gexec", "\\set", "\\if")) {
            assertFalse(executable.contains(psqlMeta), "psql-команды не выполняются драйвером: " + psqlMeta);
        }
        assertFalse(executable.toLowerCase().contains("password"), "пароли только в env");
        assertFalse(executable.contains("trust"), "auth не трогаем");
    }

    @Test
    void migrationStatusStillMatchesAfterScriptApplied() {
        DbProperties properties = new DbProperties();
        properties.setExpectedMigrations(4);

        assertTrue(new DbMigrationStatus(jdbc, properties).snapshot().ready(),
                "скрипт инициализации не ломает счётчик миграций");
    }

    private FileSystemResource script() {
        Path path = Files.exists(SCRIPT) ? SCRIPT : Path.of("infra", "initdb", "10-init-db.sql");
        return new FileSystemResource(path.toAbsolutePath());
    }
}