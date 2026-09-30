package com.workspace.storm.event.db;

import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.maintenance.SourceErrorRecorder;
import com.workspace.storm.event.db.repository.SourceErrorLogRepository;
import com.workspace.storm.event.db.support.PostgresTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ошибка источника пишется «по пути»: сбой журнала не должен ронять
 * пользовательский запрос (шаг 327).
 */
@Tag("db")
class SourceErrorRecorderTest extends PostgresTestSupport {

    @Autowired
    private SourceErrorRecorder recorder;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void writesErrorRow() {
        UUID requestId = UUID.randomUUID();

        recorder.record(requestId, "bzd", "TIMEOUT", "источник не ответил", 5000);

        Integer count = namedJdbc.queryForObject(
                "SELECT count(*) FROM storm.source_error_log WHERE request_id = :requestId",
                Map.of("requestId", requestId), Integer.class);
        assertEquals(1, count != null ? count : 0);
    }

    @Test
    void swallowsRepositoryFailure() {
        SourceErrorLogRepository failing = new SourceErrorLogRepository(namedJdbc) {
            @Override
            public void insert(SourceErrorLogEntity entity) {
                throw new DataAccessResourceFailureException("БД недоступна");
            }
        };
        SourceErrorRecorder failingRecorder = new SourceErrorRecorder(failing);

        assertDoesNotThrow(() -> failingRecorder.record(UUID.randomUUID(), "bzd", "TIMEOUT", "текст", 1),
                "сбой записи журнала не пробрасывается в пользовательский поток");
    }

    @Test
    void errorRowSurvivesForDiagnostics() {
        UUID requestId = UUID.randomUUID();

        recorder.record(requestId, "ticketbus", "HTTP_5XX", "502", null);

        SourceErrorLogEntity stored = sourceErrorLogRepository.findBySource("ticketbus").getFirst();
        assertEquals(requestId, stored.getRequestId());
        assertEquals("HTTP_5XX", stored.getCode());
        assertEquals("502", stored.getMessage());
        assertEquals(Instant.now().toEpochMilli() / 1000,
                stored.getCreatedAt().getEpochSecond(), 5);
    }
}