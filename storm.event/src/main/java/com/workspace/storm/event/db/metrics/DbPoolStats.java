package com.workspace.storm.event.db.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Состояние пула соединений из Hikari MXBean.
 *
 * <p>Фактическое время ожидания соединения не измеряем: обёртка вокруг
 * {@code DataSource} сломала бы доступ к {@code HikariDataSource} в тестах и
 * в конфигурации, а грубого сигнала «все соединения заняты, потоки ждут»
 * достаточно, чтобы понять, что упёрлись в пул (шаг 332).
 */
@Component
public class DbPoolStats {

    private static final String UNKNOWN_POOL = "unknown";

    private final DataSource dataSource;

    public DbPoolStats(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PoolSnapshot snapshot() {
        return hikari()
                .map(DbPoolStats::snapshotOf)
                .orElseGet(() -> new PoolSnapshot(UNKNOWN_POOL, 0, 0, 0, 0, 0, false));
    }

    private Optional<HikariDataSource> hikari() {
        if (dataSource instanceof HikariDataSource hikari) {
            return Optional.of(hikari);
        }
        try {
            return Optional.ofNullable(dataSource.unwrap(HikariDataSource.class));
        } catch (SQLException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static PoolSnapshot snapshotOf(HikariDataSource hikari) {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            return new PoolSnapshot(hikari.getPoolName(), 0, 0, 0, hikari.getMaximumPoolSize(), 0, false);
        }
        return new PoolSnapshot(
                hikari.getPoolName(),
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                hikari.getMaximumPoolSize(),
                pool.getThreadsAwaitingConnection(),
                true);
    }

    /** Срез пула. */
    public record PoolSnapshot(
            String pool,
            int active,
            int idle,
            int total,
            int maximum,
            int threadsAwaiting,
            boolean poolStatsAvailable) {
    }
}