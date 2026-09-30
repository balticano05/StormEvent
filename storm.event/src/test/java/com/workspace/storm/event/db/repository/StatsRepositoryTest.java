package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.StatsSourceHourlyEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class StatsRepositoryTest extends PostgresTestSupport {

    private static Instant hour(int hoursAgo) {
        return Instant.now().minus(hoursAgo, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
    }

    private int count(String source, String code, Instant at) {
        Integer value = jdbc.queryForObject(
                "SELECT count FROM storm.stats_source_hourly WHERE source = ? AND code = ? AND hour = ?",
                Integer.class, source, code, java.sql.Timestamp.from(at));
        return value != null ? value : 0;
    }

    @Test
    void firstEventCreatesRow() {
        Instant at = hour(1);

        statsRepository.incrementHourly(at, "1day", "TIMEOUT");

        assertEquals(1, count("1day", "TIMEOUT", at));
    }

    @Test
    void repeatedEventsAccumulateInOneRow() {
        Instant at = hour(1);
        for (int i = 0; i < 5; i++) {
            statsRepository.incrementHourly(at, "1day", "TIMEOUT");
        }

        assertEquals(5, count("1day", "TIMEOUT", at));
        assertEquals(1, rowCount());
    }

    @Test
    void upsertKeepsBucketsApart() {
        Instant current = hour(1);
        Instant previous = hour(2);
        statsRepository.incrementHourly(current, "1day", "TIMEOUT");
        statsRepository.incrementHourly(previous, "1day", "TIMEOUT");
        statsRepository.incrementHourly(previous, "1day", "TIMEOUT");
        statsRepository.incrementHourly(current, "1day", "PARSE");

        assertEquals(1, count("1day", "TIMEOUT", current));
        assertEquals(2, count("1day", "TIMEOUT", previous));
        assertEquals(1, count("1day", "PARSE", current));
        assertEquals(3, rowCount(), "четыре события должны лежать в трёх часовых бакетах");
    }

    @Test
    void upsertAcrossMonthBoundaryKeepsSeparateBuckets() {
        Instant february = Instant.parse("2026-02-01T00:00:00Z");
        Instant march = Instant.parse("2026-03-01T00:00:00Z");
        statsRepository.incrementHourly(february, "1day", "TIMEOUT");
        statsRepository.incrementHourly(march, "1day", "TIMEOUT");
        statsRepository.incrementHourly(march, "1day", "TIMEOUT");

        assertEquals(1, count("1day", "TIMEOUT", february));
        assertEquals(2, count("1day", "TIMEOUT", march));
    }

    @Test
    void topErrorsAreOrderedByCount() {
        Instant at = hour(1);
        for (int i = 0; i < 7; i++) {
            statsRepository.incrementHourly(at, "1day", "TIMEOUT");
        }
        statsRepository.incrementHourly(at, "3day", "PARSE");
        statsRepository.incrementHourly(at, "3day", "PARSE");

        List<StatsSourceHourlyEntity> top = statsRepository.selectTopErrors(hour(24), 10);

        assertEquals(2, top.size());
        assertEquals("TIMEOUT", top.get(0).getCode());
        assertEquals(7, top.get(0).getCount());
        assertEquals("PARSE", top.get(1).getCode());
        assertEquals(2, top.get(1).getCount());
    }

    @Test
    void topErrorsRespectsSinceAndLimit() {
        Instant old = hour(48);
        Instant fresh = hour(1);
        for (int i = 0; i < 3; i++) {
            statsRepository.incrementHourly(old, "1day", "TIMEOUT");
        }
        statsRepository.incrementHourly(fresh, "1day", "TIMEOUT");

        assertEquals(1, statsRepository.selectTopErrors(hour(24), 10).size());
        assertEquals(1, statsRepository.selectTopErrors(hour(72), 1).size());
    }

    @Test
    void topErrorsOnEmptyStats() {
        assertTrue(statsRepository.selectTopErrors(hour(24), 10).isEmpty());
    }

    private int rowCount() {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM storm.stats_source_hourly", Integer.class);
        return value != null ? value : 0;
    }
}
