package com.workspace.storm.event.db.maintenance;

import com.workspace.storm.event.config.DbProperties;
import com.workspace.storm.event.db.entity.CachedResultEntity;
import com.workspace.storm.event.db.entity.IdempotencyEntity;
import com.workspace.storm.event.db.entity.RequestLogEntity;
import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.metrics.DbQueryMetrics;
import com.workspace.storm.event.db.repository.CacheRepository;
import com.workspace.storm.event.db.repository.IdempotencyRepository;
import com.workspace.storm.event.db.repository.RequestLogRepository;
import com.workspace.storm.event.db.repository.SessionRepository;
import com.workspace.storm.event.db.repository.SourceErrorLogRepository;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Уборка идёт ограниченными порциями и не блокирует пользовательский поток
 * (шаги 347-351, ADR-043).
 */
@Tag("db")
class RetentionServiceTest extends PostgresTestSupport {

    private static final int FIVE_THOUSAND = 5000;
    private static final int STATEMENT_BUDGET = 8;
    private static final long FORTY_DAYS = 40L * 24 * 3600;

    @Autowired
    private RetentionService retention;

    @Autowired
    private DbQueryMetrics metrics;

    @Autowired
    private DataSource dataSource;

    @Test
    void fiveThousandJournalRowsCleanedWithinStatementBudget() {
        Instant old = Instant.now().minusSeconds(FORTY_DAYS);
        requestLogRepository.batchInsert(rows(old, FIVE_THOUSAND));

        DbQueryMetrics.Snapshot before = metrics.snapshot();
        int passes = 0;
        int deleted = 0;
        while (true) {
            int removed = retention.trimLogs(Instant.now()).total();
            if (removed == 0) {
                break;
            }
            deleted += removed;
            passes++;
        }
        long statements = metrics.snapshot().statements() - before.statements();

        assertEquals(FIVE_THOUSAND, deleted, "журнал вычищен целиком");
        assertEquals(3, passes, "порция 2000 - это три прохода на 5000 записей");
        assertTrue(statements <= STATEMENT_BUDGET,
                "чистка уложилась в бюджет операторов, а не по одному на запись: " + statements
                        + " операторов на " + passes + " прохода");
        assertEquals(0, count("storm.request_log"));
    }

    @Test
    void batchSizeLimitsOnePass() {
        DbProperties small = new DbProperties();
        small.setCleanupBatch(1000);
        RetentionService service = retentionWith(small);
        Instant old = Instant.now().minusSeconds(FORTY_DAYS);
        requestLogRepository.batchInsert(rows(old, 2500));

        assertEquals(1000, service.trimLogs(Instant.now()).total(), "за проход удаляется не больше порции");
        assertEquals(1500, count("storm.request_log"));
        assertEquals(1000, service.trimLogs(Instant.now()).total());
        assertEquals(500, service.trimLogs(Instant.now()).total());
        assertEquals(0, count("storm.request_log"));
    }

    @Test
    void expiredSessionsIdempotencyAndCacheAreCleanedTogether() {
        Instant now = Instant.now();
        sessionRepository.insert(newSession(now.minusSeconds(SESSION_TTL_SECONDS + 60)));
        idempotencyRepository.putIfAbsent(idempotency(UUID.randomUUID(), now.minusSeconds(600)));
        cacheRepository.put(cache("stale", now.minusSeconds(60)));
        cacheRepository.put(cache("fresh", now.plusSeconds(600)));

        RetentionService.SweepResult result = retention.cleanExpired(now);

        assertEquals(1, result.sessions());
        assertEquals(1, result.idempotency());
        assertEquals(1, result.cache());
        assertEquals(0, count("storm.session"));
        assertEquals(0, count("storm.idempotency"));
        assertEquals(1, count("storm.cached_result"));
        assertTrue(cacheRepository.get("fresh").isPresent(), "свежая запись кэша не тронута");
    }

    @Test
    void cleanExpiredOnEmptyDatabaseDoesNothing() {
        assertEquals(0, retention.cleanExpired(Instant.now()).total());
    }

    @Test
    void cleanupSkipsRowLockedByAnotherConnection() throws Exception {
        Instant old = Instant.now().minusSeconds(FORTY_DAYS);
        requestLogRepository.batchInsert(rows(old, 100));
        long lockedId = jdbc.queryForList(
                "SELECT id FROM storm.request_log_default LIMIT 1", Long.class).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "вставленные строки не оказались в _default: SKIP LOCKED нечего проверять"));

        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            lockRow(locker, lockedId);

            int deleted = retention.trimLogs(Instant.now()).requestLog();

            assertEquals(99, deleted, "занятая строка пропущена вместо ожидания блокировки");
            locker.rollback();
        }
        assertEquals(1, count("storm.request_log"), "пропущенная строка дожила до следующего прохода");
    }

    /**
     * У каждого журнала свой срок: общий вычистил бы историю ошибок источников
     * на два месяца раньше, чем обещает ADR-033 (90 дней).
     */
    @Test
    void eachLogKeepsItsOwnRetention() {
        Instant now = Instant.now();
        UUID sessionId = insertSession();
        // 40 дней назад: старше request_log (30), моложе source_error_log (90).
        Instant fortyDaysAgo = now.minusSeconds(FORTY_DAYS);
        requestLogRepository.batchInsert(rows(sessionId, fortyDaysAgo, 3));
        sourceErrorLogRepository.insert(sourceError(fortyDaysAgo));
        sourceErrorLogRepository.insert(sourceError(fortyDaysAgo));
        sourceErrorLogRepository.insert(sourceError(fortyDaysAgo));

        RetentionService.SweepResult result = retention.trimLogs(now);

        assertEquals(3, result.requestLog(), "журнал запросов чистится по своему 30-дневному сроку");
        assertEquals(0, result.sourceErrors(), "журнал ошибок живёт 90 дней и 40-дневные записи не тронуты");
        assertEquals(0, count("storm.request_log"));
        assertEquals(3, count("storm.source_error_log"));
    }

    /** Ровно на границе срока запись ещё жива: срез строгий, не включительный. */
    @Test
    void rowExactlyAtRetentionBoundarySurvives() {
        Instant now = Instant.now();
        DbProperties properties = new DbProperties();
        properties.setRequestLogRetentionDays(30);
        RetentionService service = retentionWith(properties);
        Instant boundary = now.minusSeconds(30L * 24 * 3600);

        requestLogRepository.batchInsert(rows(insertSession(), boundary.plusSeconds(1), 1));

        assertEquals(0, service.trimLogs(now).requestLog(), "запись на границе срока ещё не протухла");
    }

    private void lockRow(Connection connection, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM storm.request_log_default WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, id);
            statement.executeQuery();
        }
    }

    private RetentionService retentionWith(DbProperties properties) {
        return new RetentionService(
                sessionRepository,
                idempotencyRepository,
                cacheRepository,
                requestLogRepository,
                sourceErrorLogRepository,
                properties);
    }

    private List<RequestLogEntity> rows(Instant createdAt, int count) {
        return rows(insertSession(), createdAt, count);
    }

    private List<RequestLogEntity> rows(UUID sessionId, Instant createdAt, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> requestLog(sessionId, createdAt.plusSeconds(i)))
                .toList();
    }

    private SourceErrorLogEntity sourceError(Instant createdAt) {
        SourceErrorLogEntity entity = new SourceErrorLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSource("atlasbus");
        entity.setCode("TIMEOUT");
        entity.setMessage("источник не ответил");
        entity.setLatencyMs(1000);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private RequestLogEntity requestLog(UUID sessionId, Instant createdAt) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setText("вопрос");
        entity.setIntentJson("{\"city\":\"minsk\"}");
        entity.setStatus("OK");
        entity.setDurationMs(100);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private IdempotencyEntity idempotency(UUID requestId, Instant createdAt) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setRequestId(requestId);
        entity.setResponseJson("{}");
        entity.setCreatedAt(createdAt);
        entity.setExpiresAt(createdAt.plusSeconds(300));
        return entity;
    }

    private CachedResultEntity cache(String key, Instant expiresAt) {
        CachedResultEntity entity = new CachedResultEntity();
        entity.setCacheKey(key);
        entity.setSource("bzd");
        entity.setDomain("weather");
        entity.setPayloadJson("{\"t\":18}");
        entity.setCreatedAt(expiresAt.minusSeconds(300));
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

}