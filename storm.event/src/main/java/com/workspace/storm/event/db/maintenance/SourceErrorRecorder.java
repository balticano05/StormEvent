package com.workspace.storm.event.db.maintenance;

import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import com.workspace.storm.event.db.repository.SourceErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись ошибки источника «по пути»: ошибка не должна ломать основной поток
 * (шаг 327).
 *
 * <p>Если БД недоступна или журнал не пишется, пользовательский запрос всё
 * равно должен получить ответ: исключение здесь глотается и пишется в лог.
 * Иначе диагностическая таблица могла бы обрушить весь сервис.
 */
@Component
@RequiredArgsConstructor
public class SourceErrorRecorder {

    private static final Logger log = LoggerFactory.getLogger(SourceErrorRecorder.class);

    private final SourceErrorLogRepository repository;

    public void record(UUID requestId, String source, String code, String message, Integer latencyMs) {
        try {
            repository.insert(entry(requestId, source, code, message, latencyMs));
        } catch (RuntimeException e) {
            log.warn("Не записал ошибку источника {} {}: {}", source, code, e.toString());
        }
    }

    private SourceErrorLogEntity entry(UUID requestId, String source, String code, String message, Integer latencyMs) {
        SourceErrorLogEntity entity = new SourceErrorLogEntity();
        entity.setRequestId(requestId);
        entity.setSource(source);
        entity.setCode(code);
        entity.setMessage(message);
        entity.setLatencyMs(latencyMs);
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}