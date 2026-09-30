package com.workspace.storm.event.db;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.entity.SessionMessageEntity;
import com.workspace.storm.event.db.metrics.DbQueryMetrics;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Нагрузочный сценарий вопрос-ответ (шаги 337-343): метрики собираются,
 * медленных запросов нет, история сессии читается быстро.
 *
 * <p>Объём намеренно небольшой: проверяем не абсолютные миллисекунды CI-машины,
 * а отсутствие вырожденных запросов и полноту сбора метрик. Бюджеты времени
 * заданы с запасом относительно измеренного на ноутбуке и ослабляются
 * свойством {@code -Dstorm.load.budget.factor}, потому что контейнер
 * Testcontainers общий на весь прогон и делит ресурсы с соседними тестами.
 */
@Tag("db")
class DatabaseStressTest extends PostgresTestSupport {

    private static final int QUESTIONS = 200;

    /** Опорное время чтения истории на ноутбуке, мс. */
    private static final long HISTORY_REFERENCE_MS = 200;

    /** Опорное время batch insert 5000 строк на ноутбуке, мс. */
    private static final long BATCH_REFERENCE_MS = 5_000;

    /** Множитель бюджетов: меньше 1 — строже. */
    private static final double BUDGET_FACTOR = Double.parseDouble(
            System.getProperty("storm.load.budget.factor", "1"));

    private static long historyBudgetMs() {
        return Math.round(HISTORY_REFERENCE_MS * BUDGET_FACTOR);
    }

    private static long batchBudgetMs() {
        return Math.round(BATCH_REFERENCE_MS * BUDGET_FACTOR);
    }

    @Autowired
    private DbQueryMetrics metrics;

    @Test
    void questionFlowStaysWithinBudgetAndIsFullyMeasured() {
        DbQueryMetrics.Snapshot before = metrics.snapshot();

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < QUESTIONS; i++) {
            durations.add(answer());
        }

        DbQueryMetrics.Snapshot after = metrics.snapshot();

        assertEquals(0, after.failures() - before.failures(), "ни один запрос БД не упал");
        assertTrue(after.byCall().keySet().containsAll(List.of(
                        "SessionRepository.insert",
                        "SessionMessageRepository.insert",
                        "RequestLogRepository.insert",
                        "RequestLogRepository.findBySessionId")),
                "все вызовы репозиториев попали в метрики: " + after.byCall().keySet());
        assertEquals(0, after.slowStatements() - before.slowStatements(),
                "ни один запрос не превысил бюджет");
    }

    @Test
    void historyQueryForOneSessionStaysFast() {
        UUID sessionId = insertSession();
        for (int i = 0; i < 50; i++) {
            RequestLogEntity log = requestLog(sessionId, Instant.now().plusSeconds(i));
            requestLogRepository.insert(log);
        }

        long startedAt = System.nanoTime();
        List<RequestLogEntity> history = requestLogRepository.findBySessionId(sessionId);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(50, history.size());
        assertTrue(durationMs < historyBudgetMs(),
                "история сессии прочитана за " + durationMs + " мс, бюджет "
                        + historyBudgetMs() + " мс (опорное " + HISTORY_REFERENCE_MS
                        + " мс × " + BUDGET_FACTOR + ")");
    }

    @Test
    void bulkInsertOfFiveThousandRowsStaysWithinBudget() {
        Instant now = Instant.now();
        UUID sessionId = insertSession();
        List<RequestLogEntity> rows = new ArrayList<>(QUESTIONS * 25);
        for (int i = 0; i < QUESTIONS * 25; i++) {
            rows.add(requestLog(sessionId, now.plusSeconds(i)));
        }

        long startedAt = System.nanoTime();
        requestLogRepository.batchInsert(rows);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(QUESTIONS * 25, count());
        assertTrue(durationMs < batchBudgetMs(), "batch insert 5000 строк занял " + durationMs
                + " мс при бюджете " + batchBudgetMs() + " мс");
    }

    private long answer() {
        UUID sessionId = insertSession();
        UUID requestId = UUID.randomUUID();

        SessionMessageEntity question = new SessionMessageEntity();
        question.setSessionId(sessionId);
        question.setRole("user");
        question.setKind("search");
        question.setText("когда электричка");
        question.setRequestId(requestId);
        question.setCreatedAt(Instant.now());
        sessionMessageRepository.insert(question);

        SessionMessageEntity answer = new SessionMessageEntity();
        answer.setSessionId(sessionId);
        answer.setRole("agent");
        answer.setKind("answer");
        answer.setText("в 18:40");
        answer.setRequestId(requestId);
        answer.setCreatedAt(Instant.now().plusMillis(10));
        sessionMessageRepository.insert(answer);

        requestLogRepository.insert(requestLog(sessionId, requestId, Instant.now()));

        return requestLogRepository.findBySessionId(sessionId).size();
    }

    private RequestLogEntity requestLog(UUID sessionId, Instant createdAt) {
        return requestLog(sessionId, null, createdAt);
    }

    private RequestLogEntity requestLog(UUID sessionId, UUID requestId, Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId != null ? requestId : UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setText("когда электричка");
        entity.setIntentJson("{\"intent\":\"schedule\"}");
        entity.setStatus("OK");
        entity.setDurationMs(120);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private int count() {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM storm.request_log", Integer.class);
        return value == null ? 0 : value;
    }
}