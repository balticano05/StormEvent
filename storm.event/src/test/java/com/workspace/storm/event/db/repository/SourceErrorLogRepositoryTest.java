package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
class SourceErrorLogRepositoryTest extends PostgresTestSupport {

    private SourceErrorLogEntity entry(String source, String code, Instant createdAt) {
        SourceErrorLogEntity entity = new SourceErrorLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSource(source);
        entity.setCode(code);
        entity.setMessage("источник не ответил");
        entity.setLatencyMs(5000);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void insertAndRead() {
        UUID requestId = UUID.randomUUID();
        SourceErrorLogEntity entity = entry("1day", "TIMEOUT", Instant.now());
        entity.setRequestId(requestId);

        sourceErrorLogRepository.insert(entity);

        List<SourceErrorLogEntity> found = sourceErrorLogRepository.findBySource("1day");
        assertEquals(1, found.size());
        SourceErrorLogEntity stored = found.getFirst();
        assertEquals(requestId, stored.getRequestId());
        assertEquals("1day", stored.getSource());
        assertEquals("TIMEOUT", stored.getCode());
        assertEquals(5000, stored.getLatencyMs());
    }

    @Test
    void countSinceFiltersBySourceAndCode() {
        Instant since = Instant.now().minusSeconds(3600);
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", Instant.now()));
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", nowMinus(2 * 3600)));
        sourceErrorLogRepository.insert(entry("1day", "PARSE", nowMinus(2 * 3600)));
        sourceErrorLogRepository.insert(entry("3day", "TIMEOUT", nowMinus(2 * 3600)));

        assertEquals(1, sourceErrorLogRepository.countSince("1day", "TIMEOUT", since));
    }

    @Test
    void countSinceIgnoresOlderEntries() {
        Instant old = Instant.now().minusSeconds(48 * 3600);
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", old));

        assertEquals(0, sourceErrorLogRepository.countSince("1day", "TIMEOUT", Instant.now().minusSeconds(3600)));
    }

    @Test
    void countSinceOnEmptyWindow() {
        assertEquals(0, sourceErrorLogRepository.countSince("1day", "TIMEOUT", Instant.now()));
    }

    @Test
    void batchInsertWritesAllRows() {
        Instant now = Instant.now();
        List<SourceErrorLogEntity> batch = IntStream.range(0, 100)
                .mapToObj(i -> entry("3day", "TIMEOUT", now.plusSeconds(i)))
                .toList();

        sourceErrorLogRepository.batchInsert(batch);

        assertEquals(100, sourceErrorLogRepository.findBySource("3day").size());
    }

    @Test
    void retentionDeleteRemovesOnlyOldRows() {
        Instant now = Instant.now();
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", now.minusSeconds(40 * 24 * 3600)));
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", now));

        assertEquals(1, sourceErrorLogRepository.deleteOlderThan(now.minusSeconds(30 * 24 * 3600), 1000));
        assertEquals(1, sourceErrorLogRepository.findBySource("1day").size());
    }

    @Test
    void retentionDeleteRespectsLimit() {
        Instant old = Instant.now().minusSeconds(40 * 24 * 3600);
        sourceErrorLogRepository.batchInsert(IntStream.range(0, 4)
                .mapToObj(i -> entry("1day", "TIMEOUT", old))
                .toList());

        assertEquals(1, sourceErrorLogRepository.deleteOlderThan(old.plusSeconds(1), 1));
        assertEquals(3, sourceErrorLogRepository.findBySource("1day").size());
    }

    @Test
    void retentionDeleteOnEmptyTable() {
        assertEquals(0, sourceErrorLogRepository.deleteOlderThan(Instant.now(), 100));
    }

    @Test
    void storesProviderMessageVerbatim() {
        SourceErrorLogEntity entity = entry("1day", "TIMEOUT", Instant.now());
        entity.setMessage("<html>503 Service Unavailable: upstream '</html>".repeat(1000));

        sourceErrorLogRepository.insert(entity);

        assertEquals(entity.getMessage(), sourceErrorLogRepository.findBySource("1day").getFirst().getMessage());
    }

    @Test
    void historyIsNewestFirst() {
        Instant now = Instant.now();
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", now.minusSeconds(60)));
        sourceErrorLogRepository.insert(entry("1day", "TIMEOUT", now));

        List<SourceErrorLogEntity> found = sourceErrorLogRepository.findBySource("1day");
        assertTrue(found.get(0).getCreatedAt().isAfter(found.get(1).getCreatedAt()));
    }

    private static Instant nowMinus(long seconds) {
        return Instant.now().minusSeconds(seconds);
    }
}
