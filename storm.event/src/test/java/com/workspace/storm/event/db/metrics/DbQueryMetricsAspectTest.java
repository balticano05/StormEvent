package com.workspace.storm.event.db.metrics;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Счётчики БД заполняются вызовами репозиториев (шаги 330, 335, 359-361).
 *
 * <p>Настройки и счётчики - общие для всех тестов контекста, поэтому проверки
 * идут по дельте, а изменённые пороги возвращаются на место.
 */
@Tag("db")
class DbQueryMetricsAspectTest extends PostgresTestSupport {

    private long slowQueryMs;
    private int slowSampleSize;
    private int errorAlertPerMinute;
    private int poolWarnThreads;

    @Autowired
    private DbQueryMetrics metrics;

    @Autowired
    private DbMetricsProperties properties;

    @BeforeEach
    void rememberThresholds() {
        slowQueryMs = properties.getSlowQueryMs();
        slowSampleSize = properties.getSlowSampleSize();
        errorAlertPerMinute = properties.getErrorAlertPerMinute();
        poolWarnThreads = properties.getPoolWarnThreads();
    }

    @AfterEach
    void restoreThresholds() {
        properties.setSlowQueryMs(slowQueryMs);
        properties.setSlowSampleSize(slowSampleSize);
        properties.setErrorAlertPerMinute(errorAlertPerMinute);
        properties.setPoolWarnThreads(poolWarnThreads);
    }

    @Test
    void countsRepositoryCallsAndRows() {
        UUID sessionId = insertSession();
        DbQueryMetrics.Snapshot before = metrics.snapshot();

        sessionRepository.findById(sessionId);
        sessionRepository.findAllByState("NEW");

        DbQueryMetrics.Snapshot after = metrics.snapshot();
        assertEquals(before.statements() + 2, after.statements(),
                "два вызова репозитория - два оператора");
        assertTrue(after.byCall().getOrDefault("SessionRepository.findById", 0L) >= 1);
        assertTrue(after.byCall().getOrDefault("SessionRepository.findAllByState", 0L) >= 1);
        assertTrue(after.rowsRead() > before.rowsRead(),
                "прочитанные строки считаются по размеру выборки");
        assertEquals(before.failures(), after.failures(), "успешные вызовы не считаются ошибками");
    }

    @Test
    void countsRowsOfUpdateByAffectedRows() {
        UUID sessionId = insertSession();
        DbQueryMetrics.Snapshot before = metrics.snapshot();

        sessionRepository.updateState(sessionId, "ACTIVE");

        assertTrue(metrics.snapshot().rowsRead() > before.rowsRead(),
                "число строк для update берётся из числа затронутых строк");
    }

    @Test
    void recordsFailedCallAndRethrows() {
        DbQueryMetrics.Snapshot before = metrics.snapshot();

        assertThrows(DataAccessException.class, () -> requestLogRepository.insert(entityWithBadStatus()));

        DbQueryMetrics.Snapshot after = metrics.snapshot();
        assertEquals(before.statements() + 1, after.statements());
        assertEquals(before.failures() + 1, after.failures());
        assertTrue(after.failuresByCall().keySet().stream()
                        .anyMatch(key -> key.startsWith("RequestLogRepository.insert ->")),
                "ошибка видна по вызову, а не только в общем счётчике: " + after.failuresByCall());
    }

    @Test
    void slowCallIsSampledWithDurationAndCall() {
        long slowMs = properties.getSlowQueryMs() + 1;
        long slowBefore = metrics.snapshot().slowStatements();

        metrics.recordSuccess("CacheRepository.get", slowMs * 1_000_000L, 1);
        metrics.recordSuccess("CacheRepository.get", slowMs * 1_000_000L, 1);
        metrics.recordSuccess("CacheRepository.get", 1_000_000L, 1);

        assertEquals(slowBefore + 2, metrics.snapshot().slowStatements(),
                "в выборку попадают только вызовы дольше порога");
        DbQueryMetrics.SlowStatement newest = metrics.slowStatements(1).getFirst();
        assertEquals("CacheRepository.get", newest.call());
        assertTrue(newest.durationMs() >= properties.getSlowQueryMs());
    }

    @Test
    void sampleRingKeepsLastCalls() {
        properties.setSlowSampleSize(3);

        for (int i = 0; i < 10; i++) {
            metrics.recordSuccess("CacheRepository.get", 5_000_000_000L, i);
        }

        assertEquals(3, metrics.slowStatements(10).size(),
                "кольцо медленных запросов ограничено размером из настроек");
        assertEquals(9, metrics.slowStatements(10).getFirst().rows(),
                "в кольце остаются последние вызовы");
    }

    @Test
    void alertCountsErrorsPerWindow() {
        DbAlertEvaluator evaluator = new DbAlertEvaluator(properties);
        properties.setErrorAlertPerMinute(2);

        evaluator.checkError("SessionRepository.findById", "DataAccessException");
        evaluator.checkError("SessionRepository.findById", "DataAccessException");

        assertEquals(2, evaluator.alerts().get("db.query.errors.perMinute"));
        assertEquals(2, evaluator.alerts().get("db.query.errors.threshold"));
    }

    @Test
    void alertStaysSilentBelowThreshold() {
        DbAlertEvaluator evaluator = new DbAlertEvaluator(properties);
        properties.setErrorAlertPerMinute(5);

        evaluator.checkError("SessionRepository.findById", "DataAccessException");

        assertEquals(1, evaluator.alerts().get("db.query.errors.perMinute"),
                "одна ошибка при пороге 5 - это не нарушение");
    }

    @Test
    void alertCountsBudgetBreaches() {
        DbAlertEvaluator evaluator = new DbAlertEvaluator(properties);

        evaluator.checkBudget("SessionRepository.findAllByState", properties.getQueryBudgetMs() + 1);
        evaluator.checkBudget("SessionRepository.findAllByState", 1);

        assertEquals(1, evaluator.alerts().get("db.query.budget.breachesInWindow"),
                "быстрый вызов бюджет не расходует");
    }

    @Test
    void poolAlertOnlyWhenAllConnectionsAreBusy() {
        DbAlertEvaluator evaluator = new DbAlertEvaluator(properties);
        properties.setPoolWarnThreads(1);

        evaluator.checkPool(new DbPoolStats.PoolSnapshot("pool", 3, 3, 6, 6, 4, true));
        assertEquals(0, evaluator.alerts().get("db.pool.warningsInWindow"),
                "есть свободные соединения - потоки не ждут");

        evaluator.checkPool(new DbPoolStats.PoolSnapshot("pool", 6, 0, 6, 6, 4, true));
        assertEquals(1, evaluator.alerts().get("db.pool.warningsInWindow"),
                "пул исчерпан и потоки ждут соединение");
    }

    private RequestLogEntity entityWithBadStatus() {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setStatus("НЕТ-ТАКОГО-СТАТУСА");
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}