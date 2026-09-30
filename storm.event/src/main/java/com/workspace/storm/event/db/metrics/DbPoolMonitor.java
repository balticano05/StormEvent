package com.workspace.storm.event.db.metrics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Фоновая проверка пула соединений (шаг 332).
 *
 * <p>Раньше порог пула проверялся только при обращении к
 * {@code /api/v1/diagnostics/db}, то есть предупреждение о переполнении
 * зависело от того, смотрит ли кто-то диагностику. В бою это означало молчание
 * именно тогда, когда пул исчерпан и запросы стоят в ожидании.
 *
 * <p>Проверка идёт по таймеру, читает только MXBean и ничего не пишет в БД.
 */
@Component
@RequiredArgsConstructor
public class DbPoolMonitor {

    private static final Logger log = LoggerFactory.getLogger(DbPoolMonitor.class);

    private final DbPoolStats poolStats;
    private final DbAlertEvaluator alerts;
    private final DbMetricsProperties properties;

    @Scheduled(fixedRateString = "${storm.db.metrics.pool-check-interval-ms:30000}")
    public void check() {
        if (!properties.isEnabled()) {
            return;
        }
        DbPoolStats.PoolSnapshot snapshot = poolStats.snapshot();
        if (!snapshot.poolStatsAvailable()) {
            log.debug("db.pool.stats недоступны: {}", snapshot.pool());
            return;
        }
        alerts.checkPool(snapshot);
    }
}