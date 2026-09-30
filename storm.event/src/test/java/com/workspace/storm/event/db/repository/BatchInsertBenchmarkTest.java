package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInsertBenchmarkTest extends PostgresTestSupport {

    private static final int ROWS = 10_000;
    private static final Duration BUDGET = Duration.ofSeconds(10);

    @Test
    void tenThousandRowsGoInOneBatch() {
        UUID sessionId = insertSession();
        Instant now = Instant.now();
        List<RequestLogEntity> batch = IntStream.range(0, ROWS)
                .mapToObj(i -> {
                    RequestLogEntity entity = new RequestLogEntity();
                    entity.setRequestId(UUID.randomUUID());
                    entity.setSessionId(sessionId);
                    entity.setText("вопрос " + i);
                    entity.setIntentJson("{\"mode\":\"search\"}");
                    entity.setStatus("OK");
                    entity.setDurationMs(i % 900);
                    entity.setCreatedAt(now.plusSeconds(i));
                    return entity;
                })
                .toList();

        long startedAt = System.nanoTime();
        requestLogRepository.batchInsert(batch);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertEquals(ROWS, requestLogRepository.findBySessionId(sessionId).size());
        assertTrue(elapsed.compareTo(BUDGET) < 0,
                "10 000 строк батчем: " + elapsed.toMillis() + " мс, бюджет " + BUDGET.toMillis() + " мс");
    }
}
