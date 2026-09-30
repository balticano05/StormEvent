package com.workspace.storm.event.db.metrics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Пороговые проверки БД без Prometheus (ADR-VL-06): нарушение пишется в лог
 * один раз за окно в минуту и остаётся видимым в диагностике.
 *
 * <p>Что проверяем: ошибки вызовов репозиториев (порог в минуту), превышение
 * бюджета одного запроса и пул, в котором соединение ждут все потоки. Бюджет
 * запроса - ADR-039, порог ошибок - шаг 331, пул - шаг 332.
 */
@Component
@RequiredArgsConstructor
public class DbAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DbAlertEvaluator.class);
    private static final long WINDOW_MS = 60_000L;

    private final DbMetricsProperties properties;

    private final AtomicLong windowStartedAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger errorsInWindow = new AtomicInteger();
    private final AtomicInteger budgetBreachesInWindow = new AtomicInteger();
    private final AtomicInteger poolWarningsInWindow = new AtomicInteger();
    private final AtomicBoolean errorAlertSent = new AtomicBoolean();

    public void checkError(String call, String errorType) {
        rollWindow();
        int errors = errorsInWindow.incrementAndGet();
        if (errors <= properties.getErrorAlertPerMinute() || !errorAlertSent.compareAndSet(false, true)) {
            return;
        }
        log.error("db.query.errors за минуту: {} при пороге {} (последняя: {} {})",
                errors, properties.getErrorAlertPerMinute(), call, errorType);
    }

    public void checkBudget(String call, long durationMs) {
        if (durationMs <= properties.getQueryBudgetMs()) {
            return;
        }
        rollWindow();
        int breaches = budgetBreachesInWindow.incrementAndGet();
        if (breaches == 1) {
            log.warn("db.query.budget превышен: {} занял {} мс при бюджете {} мс",
                    call, durationMs, properties.getQueryBudgetMs());
        }
    }

    public void checkPool(DbPoolStats.PoolSnapshot pool) {
        if (pool.threadsAwaiting() < properties.getPoolWarnThreads() || pool.active() < pool.maximum()) {
            return;
        }
        rollWindow();
        int warnings = poolWarningsInWindow.incrementAndGet();
        if (warnings == 1) {
            log.warn("db.pool.wait: {} из {} соединений заняты, потоков в ожидании {}",
                    pool.active(), pool.maximum(), pool.threadsAwaiting());
        }
    }

    /** Пороги и состояние окна - попадают в /api/v1/diagnostics/db. */
    public Map<String, Object> alerts() {
        rollWindow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db.query.errors.perMinute", errorsInWindow.get());
        result.put("db.query.errors.threshold", properties.getErrorAlertPerMinute());
        result.put("db.query.budget.ms", properties.getQueryBudgetMs());
        result.put("db.query.budget.breachesInWindow", budgetBreachesInWindow.get());
        result.put("db.pool.warnThreads", properties.getPoolWarnThreads());
        result.put("db.pool.warningsInWindow", poolWarningsInWindow.get());
        return result;
    }

    /** Сбрасывает окно, если прошла минута. Возвращает true, если окно сдвинуто. */
    private boolean rollWindow() {
        long now = System.currentTimeMillis();
        long startedAt = windowStartedAt.get();
        if (now - startedAt < WINDOW_MS || !windowStartedAt.compareAndSet(startedAt, now)) {
            return false;
        }
        errorsInWindow.set(0);
        budgetBreachesInWindow.set(0);
        poolWarningsInWindow.set(0);
        errorAlertSent.set(false);
        return true;
    }
}