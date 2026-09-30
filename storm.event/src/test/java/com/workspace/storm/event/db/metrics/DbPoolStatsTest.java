package com.workspace.storm.event.db.metrics;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Снимок пула: с HikariMXBean - числа, с чужой реализацией - честный
 * «неизвестно», без исключения (шаг 332).
 */
class DbPoolStatsTest {

    @Test
    void hikariSnapshotExposesPoolNumbers() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:1/unreachable");
        config.setMaximumPoolSize(7);
        config.setPoolName("storm-test-pool");
        config.setInitializationFailTimeout(-1);
        try (HikariDataSource hikari = new HikariDataSource(config)) {

            DbPoolStats.PoolSnapshot snapshot = new DbPoolStats(hikari).snapshot();

            assertTrue(snapshot.poolStatsAvailable());
            assertEquals("storm-test-pool", snapshot.pool());
            assertEquals(7, snapshot.maximum());
            assertTrue(snapshot.total() <= snapshot.maximum());
        }
    }

    @Test
    void foreignDataSourceIsReportedAsUnavailable() {
        DbPoolStats.PoolSnapshot snapshot = new DbPoolStats(new NotHikariDataSource()).snapshot();

        assertFalse(snapshot.poolStatsAvailable(), "нет MXBean - метрики пула недоступны");
        assertEquals("unknown", snapshot.pool());
        assertEquals(0, snapshot.total());
    }

    /** Заглушка вместо Hikari: доступ к MXBean и unwrap закрыт. */
    private static final class NotHikariDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("тест не ходит в БД");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("тест не ходит в БД");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("не Hikari");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}