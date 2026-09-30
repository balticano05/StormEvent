-- V3: вторичные индексы.
--
-- Правила (ADR-037):
--   * колонка внешнего ключа индексируется всегда, если левая часть индекса
--     не покрыта первичным ключом;
--   * на каждую выборку репозиториев есть индекс, иначе SeqScan.

-- session: истечение считается от last_access_at и продлевается обращением
-- (ADR-VL-02, ADR-038); чистка идёт по expires_at
CREATE INDEX idx_session_expires_at
    ON storm.session (expires_at);
CREATE INDEX idx_session_state
    ON storm.session (state);

-- session_message: выборка контекста диалога (последние 20 сообщений)
CREATE INDEX idx_session_message_session_created
    ON storm.session_message (session_id, created_at DESC);

-- cached_result: чистка протухшего + статистика по источнику
CREATE INDEX idx_cached_result_expires_at
    ON storm.cached_result (expires_at);
CREATE INDEX idx_cached_result_source_domain
    ON storm.cached_result (source, domain);

-- idempotency: чистка окна дедупликации
CREATE INDEX idx_idempotency_expires_at
    ON storm.idempotency (expires_at);

-- request_log: история сессии и выборка по времени
CREATE INDEX idx_request_log_created_at
    ON storm.request_log (created_at DESC);
CREATE INDEX idx_request_log_session_id
    ON storm.request_log (session_id);

-- source_error_log: дашборд по времени + частые ошибки источника
CREATE INDEX idx_source_error_log_created_at
    ON storm.source_error_log (created_at DESC);
CREATE INDEX idx_source_error_log_source_code_created
    ON storm.source_error_log (source, code, created_at DESC);
