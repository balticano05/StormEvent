package com.workspace.storm.event.db.support;

import com.workspace.storm.event.db.entity.SessionEntity;
import com.workspace.storm.event.db.repository.CacheRepository;
import com.workspace.storm.event.db.repository.IdempotencyRepository;
import com.workspace.storm.event.db.repository.RequestLogRepository;
import com.workspace.storm.event.db.repository.SessionMessageRepository;
import com.workspace.storm.event.db.repository.SessionRepository;
import com.workspace.storm.event.db.repository.SourceErrorLogRepository;
import com.workspace.storm.event.db.repository.SourceStateRepository;
import com.workspace.storm.event.db.repository.StatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public abstract class PostgresTestSupport extends DbConnectionTestSupport {

    protected static final int SESSION_TTL_SECONDS = 900;

    private static final String TABLES_TO_CLEAN = """
        storm.session_message,
        storm.request_log,
        storm.source_error_log,
        storm.stats_source_hourly,
        storm.cached_result,
        storm.idempotency,
        storm.session,
        storm.source_state
        """;

    private static final List<String> SEEDED_SOURCES = List.of("atlasbus", "ticketbus", "bzd", "ticketpro", "belhotel");

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected SessionMessageRepository sessionMessageRepository;

    @Autowired
    protected SourceStateRepository sourceStateRepository;

    @Autowired
    protected CacheRepository cacheRepository;

    @Autowired
    protected IdempotencyRepository idempotencyRepository;

    @Autowired
    protected RequestLogRepository requestLogRepository;

    @Autowired
    protected SourceErrorLogRepository sourceErrorLogRepository;

    @Autowired
    protected StatsRepository statsRepository;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("TRUNCATE TABLE " + TABLES_TO_CLEAN);
        SEEDED_SOURCES.forEach(source -> jdbc.update(
                "INSERT INTO storm.source_state (source, enabled, draining) VALUES (?, TRUE, FALSE)", source));
    }

    protected static void assertJsonEquals(String expected, String actual) {
        assertEquals(expected.replace(" ", ""), actual.replace(" ", ""));
    }

    protected static SessionEntity newSession(Instant lastAccessAt) {
        SessionEntity entity = new SessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setIntent("{\"from\":\"minsk\"}");
        entity.setLastAccessAt(lastAccessAt);
        entity.setCreatedAt(lastAccessAt);
        entity.setExpiresAt(lastAccessAt.plusSeconds(SESSION_TTL_SECONDS));
        entity.setTtlSeconds(SESSION_TTL_SECONDS);
        entity.setState("NEW");
        return entity;
    }

    protected UUID insertSession() {
        SessionEntity entity = newSession(Instant.now());
        sessionRepository.insert(entity);
        return entity.getId();
    }
}
