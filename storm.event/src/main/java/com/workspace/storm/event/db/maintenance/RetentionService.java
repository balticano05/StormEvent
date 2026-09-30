package com.workspace.storm.event.db.maintenance;

import com.workspace.storm.event.config.DbProperties;
import com.workspace.storm.event.db.repository.CacheRepository;
import com.workspace.storm.event.db.repository.IdempotencyRepository;
import com.workspace.storm.event.db.repository.RequestLogRepository;
import com.workspace.storm.event.db.repository.SessionRepository;
import com.workspace.storm.event.db.repository.SourceErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Один проход уборки: истёкшее и протухшее - ограниченными порциями
 * (шаги 347-348, ADR-043).
 *
 * <p>Каждая таблица чистится одним оператором на порцию: 5 000 записей
 * журнала уходят за три прохода по 2 000, а не за 5 000 отдельных
 * {@code DELETE}. Порция задаёт {@code storm.db.cleanup-batch}; ограничение
 * нужно, чтобы транзакция не держала блокировки дольше, чем длится
 * пользовательский запрос.
 *
 * <p>Планировщик вызывает это ночью (ADR-044), здесь только сам проход -
 * вызывающий решает, когда запускать.
 */
@Component
@RequiredArgsConstructor
public class RetentionService {

    private static final String REQUEST_LOG = "request_log";
    private static final String SOURCE_ERROR_LOG = "source_error_log";

    private final SessionRepository sessions;
    private final IdempotencyRepository idempotency;
    private final CacheRepository cache;
    private final RequestLogRepository requestLog;
    private final SourceErrorLogRepository sourceErrors;
    private final DbProperties properties;

    /** Убирает истёкшие сессии и протухшие записи кэша и дедупликации. */
    public SweepResult cleanExpired(Instant now) {
        int batch = properties.getCleanupBatch();
        return new SweepResult(
                sessions.deleteExpired(now, batch),
                idempotency.deleteExpired(now, batch),
                cache.deleteExpiredBefore(now, batch),
                0,
                0);
    }

    /**
     * Добирает журналы старше их собственного срока хранения порциями.
     *
     * <p>Срок у каждого журнала свой ({@code DbProperties.retentionDaysFor}):
     * {@code request_log} живёт 30 дней, {@code source_error_log} — 90.
     * Общий срок вычищал бы историю ошибок на два месяца раньше срока.
     */
    public SweepResult trimLogs(Instant now) {
        int batch = properties.getCleanupBatch();
        return new SweepResult(
                0,
                0,
                0,
                requestLog.deleteOlderThan(cutoff(now, REQUEST_LOG), batch),
                sourceErrors.deleteOlderThan(cutoff(now, SOURCE_ERROR_LOG), batch));
    }

    private Instant cutoff(Instant now, String table) {
        return now.minusSeconds((long) properties.retentionDaysFor(table) * 24 * 3600);
    }

    /** Проход уборки целиком: истёкшее и старые журналы. */
    public SweepResult runOnce(Instant now) {
        SweepResult expired = cleanExpired(now);
        SweepResult logs = trimLogs(now);
        return expired.plus(logs);
    }

    /**
     * Что удалено за проход.
     *
     * @param sessions удалено сессий
     * @param idempotency удалено записей дедупликации
     * @param cache удалено записей кэша
     * @param requestLog удалено записей журнала запросов
     * @param sourceErrors удалено записей журнала ошибок
     */
    public record SweepResult(
            int sessions,
            int idempotency,
            int cache,
            int requestLog,
            int sourceErrors) {

        int total() {
            return sessions + idempotency + cache + requestLog + sourceErrors;
        }

        SweepResult plus(SweepResult other) {
            return new SweepResult(
                    sessions + other.sessions,
                    idempotency + other.idempotency,
                    cache + other.cache,
                    requestLog + other.requestLog,
                    sourceErrors + other.sourceErrors);
        }
    }
}