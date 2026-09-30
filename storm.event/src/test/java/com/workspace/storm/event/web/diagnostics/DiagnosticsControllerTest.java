package com.workspace.storm.event.web.diagnostics;

import com.workspace.storm.event.db.metrics.DbAlertEvaluator;
import com.workspace.storm.event.db.metrics.DbPoolStats;
import com.workspace.storm.event.db.metrics.DbQueryMetrics;
import com.workspace.storm.event.db.support.DbMigrationStatus;
import com.workspace.storm.event.db.support.PgStatStatementsRepository;
import com.workspace.storm.event.web.health.DatabaseHealthIndicator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Содержимое служебного ответа: счётчики приложения и серверные планы рядом,
 * но раздельные (шаги 304, 330).
 *
 * <p>Контроллер не вызывает {@code alerts.checkPool}: порог пула проверяет
 * {@code DbPoolMonitor} по таймеру. Иначе предупреждение о переполнении пула
 * зависело бы от того, смотрит ли кто-то диагностику, и молчало именно тогда,
 * когда пул исчерпан.
 */
class DiagnosticsControllerTest {

    private final DbQueryMetrics metrics = mock(DbQueryMetrics.class);
    private final DbPoolStats poolStats = mock(DbPoolStats.class);
    private final DbAlertEvaluator alerts = mock(DbAlertEvaluator.class);
    private final DbMigrationStatus migrationStatus = mock(DbMigrationStatus.class);
    private final PgStatStatementsRepository pgStatStatements = mock(PgStatStatementsRepository.class);
    private final DatabaseHealthIndicator dbHealth = mock(DatabaseHealthIndicator.class);

    private final DiagnosticsController controller = new DiagnosticsController(
            metrics, poolStats, alerts, migrationStatus, pgStatStatements, dbHealth);

    @Test
    void databaseReportContainsCountersAlertsMigrationsAndCatalog() {
        when(poolStats.snapshot()).thenReturn(new DbPoolStats.PoolSnapshot("storm-pool", 3, 7, 10, 10, 0, true));
        when(metrics.snapshot()).thenReturn(snapshot());
        when(alerts.alerts()).thenReturn(Map.of("db.query.budget.ms", 500));
        when(migrationStatus.snapshot()).thenReturn(new DbMigrationStatus.MigrationSnapshot(4, "4", 4, true));
        when(dbHealth.catalogStats()).thenReturn(Map.of("db.tables.count", 11L));

        Map<String, Object> report = controller.database();

        assertEquals(List.of("db.pool", "db.queries", "db.alerts", "db.migrations", "db.catalog"),
                List.copyOf(report.keySet()));
        @SuppressWarnings("unchecked")
        Map<String, Object> queries = (Map<String, Object>) report.get("db.queries");
        assertEquals(42L, queries.get("db.statements.count"));
        assertEquals(7L, queries.get("db.query.failures"));
        assertEquals(1234L, queries.get("db.rows.read"));
        assertEquals(1200L, queries.get("db.query.totalMs"));
        assertEquals(300L, queries.get("db.query.maxMs"));
    }

    /** Проверка порога пула уехала в фоновый монитор, а не в обработчик запроса. */
    @Test
    void poolAlertIsNotEvaluatedAsASideEffectOfTheReport() {
        when(poolStats.snapshot()).thenReturn(
                new DbPoolStats.PoolSnapshot("storm-pool", 10, 0, 10, 10, 12, true));
        when(metrics.snapshot()).thenReturn(snapshot());
        when(alerts.alerts()).thenReturn(Map.of());
        when(migrationStatus.snapshot()).thenReturn(new DbMigrationStatus.MigrationSnapshot(4, "4", 4, true));
        when(dbHealth.catalogStats()).thenReturn(Map.of());

        controller.database();

        verify(alerts, never()).checkPool(any());
    }

    @Test
    void slowQueriesReportsServerSideAndApplicationSide() {
        when(pgStatStatements.isAvailable()).thenReturn(true);
        when(pgStatStatements.topByTotalTime(5)).thenReturn(Optional.of(List.of(
                new PgStatStatementsRepository.StatementStat("42", "SELECT 1", 7, 12.5, 1.8, 7))));

        Map<String, Object> report = controller.slowQueries(5);

        assertEquals(true, report.get("pg_stat_statements.available"));
        @SuppressWarnings("unchecked")
        List<PgStatStatementsRepository.StatementStat> serverSide =
                (List<PgStatStatementsRepository.StatementStat>) report.get("pg_stat_statements.byTotalTime");
        assertEquals(1, serverSide.size());
        assertEquals("42", serverSide.getFirst().queryId());
        verify(pgStatStatements).topByTotalTime(5);
        verify(metrics).slowStatements(5);
    }

    @Test
    void slowQueriesDegradesWithoutPgStatStatements() {
        when(pgStatStatements.isAvailable()).thenReturn(false);
        when(pgStatStatements.topByTotalTime(anyInt())).thenReturn(Optional.empty());

        Map<String, Object> report = controller.slowQueries(20);

        assertEquals(false, report.get("pg_stat_statements.available"));
        assertTrue(((List<?>) report.get("pg_stat_statements.byTotalTime")).isEmpty(),
                "без расширения отдаём пустой список, а не ошибку");
        verify(metrics).slowStatements(20);
    }

    private DbQueryMetrics.Snapshot snapshot() {
        return new DbQueryMetrics.Snapshot(
                42L, 7L, 2L, 1234L, 1200L, 300L, 28.5d,
                Map.of("SessionRepository.touch", 20L),
                Map.of("RequestLogRepository.insert -> BadSqlGrammarException", 7L),
                List.of());
    }
}