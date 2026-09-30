package com.workspace.storm.event.db.support;

import com.workspace.storm.event.config.DbProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Готовность по миграциям Flyway: сравнение, кэш и отсутствие болтовни в логе
 * (шаги 369-370).
 */
class DbMigrationStatusTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void notReadyWhenAppliedCountIsBelowExpected() {
        DbMigrationStatus status = status(3, 4, 1);

        DbMigrationStatus.MigrationSnapshot snapshot = status.snapshot();

        assertEquals(3, snapshot.applied());
        assertEquals(4, snapshot.expected());
        assertFalse(snapshot.ready(), "старая схема не принимает трафик");
        assertFalse(snapshot.ahead());
    }

    @Test
    void readyWhenCountsMatchExactly() {
        DbMigrationStatus.MigrationSnapshot snapshot = status(4, 4, 1).snapshot();

        assertTrue(snapshot.ready());
        assertFalse(snapshot.ahead());
    }

    /**
     * Откат: база новее бинаря — схема совместима, блокировать трафик незачем.
     * При строгом {@code ==} инстанс не встал бы в балансировщик после
     * возврата на предыдущую версию приложения.
     */
    @Test
    void readyWhenDatabaseIsAheadOfTheBinary() {
        DbMigrationStatus.MigrationSnapshot snapshot = status(5, 4, 1).snapshot();

        assertTrue(snapshot.ready(), "лишняя миграция не мешает обслуживать трафик");
        assertTrue(snapshot.ahead(), "но это отдельное состояние для алерта");
    }

    /** Проба /ready не должна бить по базе двумя SQL-запросами каждый раз. */
    @Test
    void snapshotIsCachedForTheConfiguredTtl() {
        DbMigrationStatus status = status(4, 4, 30_000);

        status.snapshot();
        status.snapshot();
        status.snapshot();

        verify(jdbc, times(1)).queryForObject(anyString(), eq(Integer.class));
        verify(jdbc, times(1)).queryForObject(anyString(), eq(String.class));
    }

    @Test
    void snapshotIsRefreshedWhenTtlExpires() {
        DbMigrationStatus status = status(4, 4, 1);

        status.snapshot();
        awaitTtl();
        status.snapshot();

        verify(jdbc, atLeastOnce()).queryForObject(anyString(), eq(Integer.class));
    }

    /** Неположительный TTL не должен превращать кэш в вечную правду. */
    @Test
    void nonPositiveTtlFallsBackToDefaultAndStillRefreshes() {
        DbMigrationStatus status = status(4, 4, 0);

        status.snapshot();
        awaitTtl();
        status.snapshot();

        verify(jdbc, atLeastOnce()).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    void emptyHistoryIsNotReadyRatherThanACountOfZeroFromNull() {
        JdbcTemplate empty = mock(JdbcTemplate.class);
        when(empty.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        when(empty.queryForObject(anyString(), eq(String.class))).thenReturn(null);
        DbProperties properties = new DbProperties();
        properties.setExpectedMigrations(4);

        DbMigrationStatus.MigrationSnapshot snapshot = new DbMigrationStatus(empty, properties).snapshot();

        assertEquals(0, snapshot.applied());
        assertEquals("", snapshot.version());
        assertFalse(snapshot.ready());
    }

    private DbMigrationStatus status(int applied, int expected, long ttlMs) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(applied);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn(String.valueOf(applied));
        DbProperties properties = new DbProperties();
        properties.setExpectedMigrations(expected);
        properties.setMigrationsCacheTtlMs(ttlMs);
        return new DbMigrationStatus(jdbc, properties);
    }

    private void awaitTtl() {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}