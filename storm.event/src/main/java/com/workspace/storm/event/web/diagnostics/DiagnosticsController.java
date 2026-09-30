package com.workspace.storm.event.web.diagnostics;

import com.workspace.storm.event.db.metrics.DbAlertEvaluator;
import com.workspace.storm.event.db.metrics.DbPoolStats;
import com.workspace.storm.event.db.metrics.DbQueryMetrics;
import com.workspace.storm.event.db.support.DbMigrationStatus;
import com.workspace.storm.event.db.support.PgStatStatementsRepository;
import com.workspace.storm.event.web.health.DatabaseHealthIndicator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Служебная диагностика БД (шаги 304, 330, 335).
 *
 * <p>Весь контроллер закрыт {@code AdminKeyFilter}: счётчики и планы запросов
 * показываем только по {@code X-Api-Key}. Два разных источника времени у
 * запроса намеренно не смешиваем: {@code pg_stat_statements} считает время
 * сервера, счётчики приложения - время вместе с получением соединения.
 */
@RestController
@RequiredArgsConstructor
public class DiagnosticsController {

    private static final int DEFAULT_LIMIT = 20;

    /** Больше строк из pg_stat_statements не отдаём: это текст запросов. */
    private static final int MAX_LIMIT = 200;

    private final DbQueryMetrics metrics;
    private final DbPoolStats poolStats;
    private final DbAlertEvaluator alerts;
    private final DbMigrationStatus migrationStatus;
    private final PgStatStatementsRepository pgStatStatements;
    private final DatabaseHealthIndicator dbHealth;

    @GetMapping("/api/v1/diagnostics/db")
    public Map<String, Object> database() {
        DbPoolStats.PoolSnapshot pool = poolStats.snapshot();
        DbQueryMetrics.Snapshot queries = metrics.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db.pool", pool);
        result.put("db.queries", Map.of(
                "db.statements.count", queries.statements(),
                "db.rows.read", queries.rowsRead(),
                "db.query.failures", queries.failures(),
                "db.query.slow", queries.slowStatements(),
                "db.query.totalMs", queries.totalMs(),
                "db.query.maxMs", queries.maxMs(),
                "db.query.avgMs", queries.avgMs(),
                "db.statements.byCall", queries.byCall(),
                "db.query.failures.byCall", queries.failuresByCall()));
        result.put("db.alerts", alerts.alerts());
        result.put("db.migrations", migrationStatus.snapshot());
        result.put("db.catalog", dbHealth.catalogStats());
        return result;
    }

    /**
     * Топ медленных запросов с сервера и из приложения.
     *
     * <p>{@code limit} из запроса ограничивается: значение уходит в
     * {@code LIMIT} SQL-запроса, поэтому отрицательное дало бы ошибку базы, а
     * огромное — выгрузку всей статистики в память процесса.
     */
    @GetMapping("/api/v1/diagnostics/slow-queries")
    public Map<String, Object> slowQueries(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int bounded = Math.clamp(limit, 1, MAX_LIMIT);
        List<PgStatStatementsRepository.StatementStat> serverSide =
                pgStatStatements.topByTotalTime(bounded).orElse(List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("limit", bounded);
        result.put("pg_stat_statements.available", pgStatStatements.isAvailable());
        result.put("pg_stat_statements.byTotalTime", serverSide);
        result.put("application.slowStatements", metrics.slowStatements(bounded));
        return result;
    }
}