package com.workspace.storm.event.db.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Порог пула проверяется по таймеру, а не при обращении к диагностике (шаг 332).
 *
 * <p>Раньше {@code alerts.checkPool} вызывался из обработчика
 * {@code /api/v1/diagnostics/db}: предупреждение о переполнении пула зависело
 * от того, смотрит ли кто-то диагностику, и молчало именно тогда, когда пул
 * исчерпан и запросы стоят в ожидании.
 */
class DbPoolMonitorTest {

    private final DbPoolStats poolStats = mock(DbPoolStats.class);
    private final DbAlertEvaluator alerts = mock(DbAlertEvaluator.class);
    private final DbMetricsProperties properties = new DbMetricsProperties();

    private final DbPoolMonitor monitor = new DbPoolMonitor(poolStats, alerts, properties);

    @Test
    void exhaustedPoolRaisesAlertWithoutAnyoneAsking() {
        when(poolStats.snapshot()).thenReturn(
                new DbPoolStats.PoolSnapshot("storm-pool", 10, 0, 10, 10, 12, true));

        monitor.check();

        verify(alerts).checkPool(
                new DbPoolStats.PoolSnapshot("storm-pool", 10, 0, 10, 10, 12, true));
    }

    @Test
    void healthyPoolIsCheckedToo() {
        DbPoolStats.PoolSnapshot healthy =
                new DbPoolStats.PoolSnapshot("storm-pool", 3, 7, 10, 10, 0, true);
        when(poolStats.snapshot()).thenReturn(healthy);

        monitor.check();

        verify(alerts).checkPool(healthy);
    }

    /**
     * Без MXBean (драйвер не отдаёт статистику) порог не проверяется: нулевой
     * счётчик соединений иначе выглядел бы как полностью исчерпанный пул.
     */
    @Test
    void unavailablePoolStatsAreNotTreatedAsAnEmptyPool() {
        when(poolStats.snapshot()).thenReturn(
                new DbPoolStats.PoolSnapshot("storm-pool", 0, 0, 10, 10, 0, false));

        monitor.check();

        verify(alerts, never()).checkPool(any());
    }

    @Test
    void disabledMetricsSkipTheCheckEntirely() {
        properties.setEnabled(false);
        DbPoolStats.PoolSnapshot healthy =
                new DbPoolStats.PoolSnapshot("storm-pool", 3, 7, 10, 10, 0, true);
        when(poolStats.snapshot()).thenReturn(healthy);

        monitor.check();

        verify(alerts, never()).checkPool(any());
        assertEquals(30000L, properties.getPoolCheckIntervalMs(),
                "интервал проверки пула по умолчанию — 30 с");
    }
}