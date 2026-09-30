package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.entity.StatsSourceHourlyEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("db")
class MonthBoundaryTest extends PostgresTestSupport {

    private static final Instant JANUARY = Instant.parse("2026-01-31T23:59:59Z");
    private static final Instant FEBRUARY = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant FEBRUARY_END = Instant.parse("2026-02-28T23:59:59Z");
    private static final Instant MARCH = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void requestLogKeepsRowsAroundTheBoundary() {
        requestLogRepository.insert(log(JANUARY));
        requestLogRepository.insert(log(FEBRUARY));
        requestLogRepository.insert(log(FEBRUARY_END));
        requestLogRepository.insert(log(MARCH));

        assertEquals(4, count("storm.request_log"));
        assertEquals(1, countBetween("storm.request_log", JANUARY, FEBRUARY), "январская запись");
        assertEquals(2, countBetween("storm.request_log", FEBRUARY, MARCH), "февральские записи");
    }

    @Test
    void rowsLandInDefaultPartitionUntilMonthlyOnesExist() {
        requestLogRepository.insert(log(FEBRUARY));
        requestLogRepository.insert(log(MARCH));

        Integer inDefault = jdbc.queryForObject("""
            SELECT COUNT(*) FROM storm.request_log_default WHERE created_at >= ?
            """, Integer.class, java.sql.Timestamp.from(FEBRUARY));
        assertEquals(2, inDefault != null ? inDefault : 0);
    }

    @Test
    void retentionCutsAcrossMonthBoundary() {
        requestLogRepository.insert(log(FEBRUARY));
        requestLogRepository.insert(log(MARCH));

        int deleted = requestLogRepository.deleteOlderThan(FEBRUARY.plusSeconds(1), 100);

        assertEquals(1, deleted);
        assertEquals(1, count("storm.request_log"));
    }

    @Test
    void errorLogKeepsRowsAroundTheBoundary() {
        sourceErrorLogRepository.insert(errorLog(FEBRUARY_END));
        sourceErrorLogRepository.insert(errorLog(MARCH));

        assertEquals(2, count("storm.source_error_log"));
        assertEquals(1, countBetween("storm.source_error_log", FEBRUARY, MARCH));
        assertEquals(1, countBetween("storm.source_error_log", MARCH, FEBRUARY.plus(60, ChronoUnit.DAYS)));
    }

    @Test
    void hourlyStatsBucketsAreIndependentAcrossMonths() {
        Instant februaryHour = FEBRUARY.truncatedTo(ChronoUnit.HOURS);
        Instant marchHour = MARCH.truncatedTo(ChronoUnit.HOURS);
        statsRepository.incrementHourly(februaryHour, "bzd", "TIMEOUT");
        statsRepository.incrementHourly(marchHour, "bzd", "TIMEOUT");
        statsRepository.incrementHourly(marchHour, "bzd", "TIMEOUT");

        assertEquals(1, statsCount(februaryHour));
        assertEquals(2, statsCount(marchHour));
    }

    @Test
    void topErrorsSpanBothMonths() {
        Instant februaryHour = FEBRUARY.truncatedTo(ChronoUnit.HOURS);
        Instant marchHour = MARCH.truncatedTo(ChronoUnit.HOURS);
        statsRepository.incrementHourly(februaryHour, "bzd", "TIMEOUT");
        statsRepository.incrementHourly(marchHour, "bzd", "TIMEOUT");

        List<StatsSourceHourlyEntity> top = statsRepository.selectTopErrors(februaryHour.minus(1, ChronoUnit.HOURS), 10);

        assertEquals(2, top.size());
        assertEquals(februaryHour, top.get(1).getHour());
        assertEquals(marchHour, top.get(0).getHour());
    }

    private RequestLogEntity log(Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setText("вопрос на границе месяца");
        entity.setStatus("OK");
        entity.setDurationMs(10);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private SourceErrorLogEntity errorLog(Instant createdAt) {
        SourceErrorLogEntity entity = new SourceErrorLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSource("bzd");
        entity.setCode("TIMEOUT");
        entity.setMessage("таймаут");
        entity.setLatencyMs(5000);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private int statsCount(Instant hour) {
        Integer value = jdbc.queryForObject("""
            SELECT SUM(count) FROM storm.stats_source_hourly WHERE hour = ?
            """, Integer.class, java.sql.Timestamp.from(hour));
        return value != null ? value : 0;
    }

    private int countBetween(String table, Instant from, Instant to) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE created_at >= ? AND created_at < ?",
                Integer.class, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
        return value != null ? value : 0;
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value != null ? value : 0;
    }
}
