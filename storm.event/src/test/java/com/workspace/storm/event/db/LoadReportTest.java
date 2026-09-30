package com.workspace.storm.event.db;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Замеры под нагрузкой в одном месте (шаги 337-343).
 *
 * <p>Тест печатает отчёт в stdout: с него берутся числа в
 * {@code docs/db-load-results.md}.
 *
 * <p>Проверяется не абсолютная скорость конкретной машины, а отсутствие
 * вырожденного поведения: одна операция вместо тысячи, полный перебор вместо
 * индекса. Поэтому бюджеты времени заданы с большим запасом относительно
 * измеренного на ноутбуке (обычно 10–20 раз) и переопределяются системным
 * свойством {@code -Dstorm.load.budget.factor=1} - так прогон можно ужесточить
 * на своём стенде и оставить строгим по умолчанию. Жёсткие миллисекунды в
 * дефолтном прогоне были бы лотереей на загруженном раннере: тот же контейнер
 * Testcontainers обслуживает все тесты сразу.
 */
@Tag("db")
class LoadReportTest extends PostgresTestSupport {

    private static final int HISTORY_ROWS = 20_000;

    /** Множитель бюджетов времени: меньше 1 — строже, больше — терпимее. */
    private static final double BUDGET_FACTOR = Double.parseDouble(
            System.getProperty("storm.load.budget.factor", "1"));

    private static void assertWithinBudget(String what, long elapsedMs, long referenceMs) {
        long budget = Math.round(referenceMs * BUDGET_FACTOR);
        assertTrue(elapsedMs < budget,
                what + ": " + elapsedMs + " мс при бюджете " + budget + " мс"
                        + " (опорное значение " + referenceMs + " мс, множитель " + BUDGET_FACTOR
                        + "; ослабляется свойством -Dstorm.load.budget.factor)");
    }

    @Test
    void measure() {
        report("20 000 строк истории одной сессии: чтение", readHistory(20_000));
        report("10 000 строк журнала: batch insert", batchInsert(10_000));
        report("10 000 строк журнала: чтение по сессии", readHistory(10_000));
        report("5 000 протухших записей кэша: удаление", deleteExpiredCache());
        report("5 000 протухших записей idempotency: удаление", deleteExpiredIdempotency());
        report("200 вопросов: полный цикл записи", questionFlow());
        report("очистка 40-дневного журнала: 3 прохода", trimOldJournal());
    }

    private long readHistory(int rows) {
        UUID sessionId = insertSession();
        requestLogRepository.batchInsert(rows(sessionId, rows));
        long startedAt = System.nanoTime();
        List<RequestLogEntity> history = requestLogRepository.findBySessionId(sessionId);
        long elapsed = millis(startedAt);
        assertTrue(history.size() == rows, "прочитаны все строки сессии");
        assertWithinBudget("чтение " + rows + " строк истории", elapsed, 20_000L);
        return elapsed;
    }

    private long batchInsert(int rows) {
        UUID sessionId = insertSession();
        List<RequestLogEntity> batch = rows(sessionId, rows);
        long startedAt = System.nanoTime();
        requestLogRepository.batchInsert(batch);
        long elapsed = millis(startedAt);
        assertWithinBudget("batch insert " + rows + " строк", elapsed, 60_000L);
        return elapsed;
    }

    private long deleteExpiredCache() {
        Instant now = Instant.now();
        List<CachedResultEntity> batch = IntStream.range(0, 5_000)
                .mapToObj(i -> cache("key-" + i, now.minusSeconds(3600)))
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO storm.cached_result (cache_key, source, domain, payload_json, created_at, expires_at) "
                        + "VALUES (?, 'bzd', 'weather', '{}', ?, ?)",
                batch.stream().map(entity -> new Object[]{
                        entity.getCacheKey(),
                        java.sql.Timestamp.from(entity.getCreatedAt()),
                        java.sql.Timestamp.from(entity.getExpiresAt())}).toList());
        return deletePasses(() -> cacheRepository.deleteExpiredBefore(now.minusSeconds(60), 2_000));
    }

    private long deleteExpiredIdempotency() {
        Instant now = Instant.now();
        List<Object[]> batch = IntStream.range(0, 5_000)
                .mapToObj(i -> new Object[]{
                        UUID.randomUUID(),
                        java.sql.Timestamp.from(now.minusSeconds(3600)),
                        java.sql.Timestamp.from(now.minusSeconds(1800))})
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO storm.idempotency (request_id, created_at, expires_at) VALUES (?, ?, ?)", batch);
        return deletePasses(() -> idempotencyRepository.deleteExpired(now.minusSeconds(60), 2_000));
    }

    private long deletePasses(java.util.function.IntSupplier cleanup) {
        long startedAt = System.nanoTime();
        int removed;
        int passes = 0;
        do {
            removed = cleanup.getAsInt();
            if (removed > 0) {
                passes++;
            }
        } while (removed > 0 && passes < 10);
        long elapsed = millis(startedAt);
        assertTrue(passes <= 3, "5 000 записей удалены за " + passes + " прохода");
        return elapsed;
    }

    private long questionFlow() {
        long startedAt = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            UUID sessionId = insertSession();
            requestLogRepository.insert(logRow(sessionId, Instant.now()));
            requestLogRepository.findBySessionId(sessionId);
        }
        long elapsed = millis(startedAt);
        assertWithinBudget("200 вопросов подряд", elapsed, 120_000L);
        return elapsed;
    }

    private long trimOldJournal() {
        UUID sessionId = insertSession();
        Instant old = Instant.now().minusSeconds(40L * 24 * 3600);
        requestLogRepository.batchInsert(rows(sessionId, 5_000, old));

        long startedAt = System.nanoTime();
        int removed;
        int passes = 0;
        do {
            removed = requestLogRepository.deleteOlderThan(Instant.now(), 2_000);
            if (removed > 0) {
                passes++;
            }
        } while (removed > 0 && passes < 10);
        long elapsed = millis(startedAt);
        assertTrue(passes == 3, "5 000 строк удалены за " + passes + " прохода по 2 000");
        return elapsed;
    }

    private List<RequestLogEntity> rows(UUID sessionId, int count) {
        return rows(sessionId, count, Instant.now());
    }

    private List<RequestLogEntity> rows(UUID sessionId, int count, Instant createdAt) {
        List<RequestLogEntity> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            batch.add(logRow(sessionId, createdAt.plusSeconds(i)));
        }
        return batch;
    }

    private RequestLogEntity logRow(UUID sessionId, Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setText("вопрос");
        entity.setIntentJson("{\"mode\":\"search\"}");
        entity.setStatus("OK");
        entity.setDurationMs(120);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private CachedResultEntity cache(String key, Instant expiresAt) {
        CachedResultEntity entity = new CachedResultEntity();
        entity.setCacheKey(key);
        entity.setSource("bzd");
        entity.setDomain("weather");
        entity.setPayloadJson("{}");
        entity.setCreatedAt(expiresAt.minusSeconds(300));
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    private void report(String name, long millis) {
        System.out.printf("LOADRESULT | %-48s | %d мс%n", name, millis);
    }

    private static long millis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}