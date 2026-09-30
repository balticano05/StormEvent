package com.workspace.storm.event.db.maintenance;

import com.workspace.storm.event.config.DbProperties;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Месячные партиции журналов: создаются заранее, пишутся своей датой и
 * уходят целиком (шаги 352-354, ADR-033).
 */
@Tag("db")
class PartitionMaintenanceTest extends PostgresTestSupport {

    private static final String REQUEST_LOG = "request_log";
    private static final List<String> PARTITIONED_TABLES =
            List.of("request_log", "source_error_log", "stats_source_hourly");
    private static final Pattern MONTHLY = Pattern.compile("[a-z_]+_\\d{4}_\\d{2}");

    private static final YearMonth MARCH_2027 = YearMonth.of(2027, 3);
    private static final Instant INSIDE_MARCH = Instant.parse("2027-03-15T10:00:00Z");

    @Autowired
    private PartitionMaintenance maintenance;

    /**
     * Контейнер общий на весь прогон, а партиция - объект схемы: оставлять её
     * после теста нельзя, иначе планы остальных тестов поедут.
     */
    @AfterEach
    void dropCreatedPartitions() {
        for (String table : PARTITIONED_TABLES) {
            for (String name : maintenance.listPartitions(table)) {
                if (MONTHLY.matcher(name).matches()) {
                    jdbc.execute("DROP TABLE IF EXISTS storm." + name);
                }
            }
        }
    }

    @Test
    void createsPartitionForUpcomingMonth() {
        List<String> created = maintenance.ensureUpcoming(MARCH_2027, 0);

        assertTrue(created.contains(REQUEST_LOG + "_2027_03"), "партиция месяца создана: " + created);
        assertTrue(created.contains("source_error_log_2027_03"));
        assertTrue(created.contains("stats_source_hourly_2027_03"));
    }

    @Test
    void partitionNameMatchesRowMonth() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        insertRequestLog(INSIDE_MARCH);

        String partition = jdbc.queryForObject(
                "SELECT tableoid::regclass::text FROM storm.request_log WHERE created_at = ?",
                String.class, Timestamp.from(INSIDE_MARCH));
        assertEquals(REQUEST_LOG + "_2027_03", partition,
                "строка лежит в партиции своего месяца, а не в _default");
    }

    @Test
    void ensureIsIdempotent() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        assertTrue(maintenance.ensureUpcoming(MARCH_2027, 0).isEmpty(),
                "повторный вызов не создаёт ничего");
        assertEquals(1, maintenance.listPartitions(REQUEST_LOG).stream()
                .filter((REQUEST_LOG + "_2027_03")::equals)
                .count());
    }

    @Test
    void appliesVacuumSettingsToNewPartition() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        String options = jdbc.queryForObject("""
            SELECT array_to_string(reloptions, ',') FROM pg_class
            WHERE relname = 'request_log_2027_03'
            """, String.class);

        assertNotNull(options, "у новой партиции есть reloptions");
        assertTrue(options.contains("autovacuum_vacuum_scale_factor=0.02"), options);
    }

    @Test
    void dropsOnlyOldMonthlyPartitions() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        List<String> dropped = maintenance.dropOlderThan(YearMonth.of(2027, 5), 0);

        assertTrue(dropped.contains(REQUEST_LOG + "_2027_03"), "партиция месяца удалена: " + dropped);
        assertFalse(maintenance.listPartitions(REQUEST_LOG).contains(REQUEST_LOG + "_2027_03"));
        assertTrue(maintenance.listPartitions(REQUEST_LOG).contains(REQUEST_LOG + "_default"),
                "DEFAULT-партиция остаётся всегда");
    }

    @Test
    void keepsPartitionsNewerThanBoundary() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        List<String> dropped = maintenance.dropOlderThan(MARCH_2027, 0);

        assertTrue(dropped.isEmpty(), "партиция на границе не удаляется: " + dropped);
    }

    @Test
    void ignoresForeignNamesInPartitionList() {
        List<String> before = maintenance.listPartitions(REQUEST_LOG);

        assertTrue(maintenance.dropOlderThan(YearMonth.of(2000, 1), 0).isEmpty());
        assertEquals(before, maintenance.listPartitions(REQUEST_LOG),
                "чужие имена и DEFAULT не трогаются");
    }

    /**
 * Ретеншн у каждого журнала свой: 30 дней у {@code request_log} и 90 у
 * {@code source_error_log}. Общий срок удалял бы партицию журнала ошибок на
 * два месяца раньше, чем обещает ADR-033.
 */
@Test
    void dropsPartitionsByEachLogOwnRetention() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        List<String> dropped = maintenance.dropOlderThanRetention(YearMonth.of(2027, 5));

        assertTrue(dropped.contains(REQUEST_LOG + "_2027_03"),
                "журнал запросов: 30 дней = 1 месяц, партиция марта уходит в мае: " + dropped);
        assertFalse(dropped.contains("source_error_log_2027_03"),
                "журнал ошибок живёт 90 дней = 3 месяца, партиция марта ещё нужна: " + dropped);
        assertFalse(dropped.contains("stats_source_hourly_2027_03"),
                "агрегаты живут 3 месяца, партиция марта ещё нужна: " + dropped);
    }

    /**
     * Страховка от неверного срока: при retention меньше месяца партиция
     * прошлого месяца всё равно не удаляется.
     */
@Test
    void keepsPreviousMonthEvenWhenRetentionIsTiny() {
        DbProperties tiny = new DbProperties();
        tiny.setRequestLogRetentionDays(1);
        tiny.setSourceErrorLogRetentionDays(1);
        tiny.setPartitionMinKeepMonths(1);
        PartitionMaintenance service = new PartitionMaintenance(jdbc, tiny);

        service.ensureUpcoming(MARCH_2027, 0);

        assertTrue(service.dropOlderThanRetention(YearMonth.of(2027, 4)).isEmpty(),
                "при сроке в день предыдущий месяц не трогаем");
        assertFalse(service.dropOlderThanRetention(YearMonth.of(2027, 5)).isEmpty(),
                "через месяц после границы партиция уходит");
    }

    /** Срок в днях округляется вверх до целых месяцев, а не вниз. */
    @Test
    void retentionRoundsDaysUpToMonths() {
        assertEquals(1, PartitionMaintenance.retentionMonths(1));
        assertEquals(1, PartitionMaintenance.retentionMonths(30));
        assertEquals(2, PartitionMaintenance.retentionMonths(31));
        assertEquals(3, PartitionMaintenance.retentionMonths(90));
        assertEquals(0, PartitionMaintenance.retentionMonths(0));
    }

    /**
     * Границы партиций считаются по UTC: {@code created_at} хранится в UTC,
     * а смещение зоны отправило бы часть строк не в тот месяц.
     */
    @Test
    void partitionFollowsUtcMonthNotLocalTime() {
        maintenance.ensureUpcoming(MARCH_2027, 0);

        // 23:30 по Минску 1 апреля = 20:30 UTC 31 марта: это мартовская партиция.
        insertRequestLog(Instant.parse("2027-03-31T20:30:00Z"));

        String partition = jdbc.queryForObject(
                "SELECT tableoid::regclass::text FROM storm.request_log WHERE created_at = ?",
                String.class, Timestamp.from(Instant.parse("2027-03-31T20:30:00Z")));
        assertEquals(REQUEST_LOG + "_2027_03", partition,
                "месяц определяется по UTC, как хранится created_at");
    }

    @Test
    void defaultPartitionStaysWritableAfterMaintenance() {
        maintenance.ensureUpcoming(MARCH_2027, 0);
        maintenance.dropOlderThan(YearMonth.of(2027, 5), 0);

        insertRequestLog(Instant.now());

        assertTrue(maintenance.listPartitions(REQUEST_LOG).contains(REQUEST_LOG + "_default"));
    }

    private void insertRequestLog(Instant createdAt) {
        jdbc.update("""
            INSERT INTO storm.request_log (request_id, session_id, status, created_at)
            VALUES (?, NULL, 'OK', ?)
            """, UUID.randomUUID(), Timestamp.from(createdAt));
    }
}