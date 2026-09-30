-- V4: индексы и настройки автовакуума по результатам EXPLAIN (ANALYZE, BUFFERS).
--
-- Замеры сделаны на PostgreSQL 16.15 с данных плана (шаг 301):
--   session 5 000, request_log 45 000, source_error_log 40 000,
--   cached_result 20 000, idempotency 20 000, stats_source_hourly 18 000.
-- Полные планы и цифры - docs/db-hot-queries.md.
--
-- Что изменилось по итогам замеров:
--
-- 1. idx_source_error_log_source_created. Запрос "последние 100 ошибок
--    источника" обслуживался индексом (created_at DESC) с фильтром по
--    source: 0.70 мс и 543 просмотренных строк вместо 100. С составным
--    индексом план тот же, но без фильтра: 0.16 мс, 100 строк.
--
-- 2. idx_request_log_session_created НЕ создаётся осознанно. На двух
--    распределениях (9 строк на сессию и 20 000 строк на одну сессию)
--    планировщик всё равно выбирает idx_request_log_session_id плюс
--    quicksort: 0.15 мс против 0.10 мс и 9.9 мс против 10.7 мс. Выигрыша
--    нет, лишний индекс на append-only таблице стоит места и времени
--    вставки, поэтому его нет (ADR-039 про бюджеты, а не про максимум
--    индексов).
--
-- 3. Автовакуум. Журналы и кэш пишутся постоянно и почти не обновляются:
--    при дефолтном scale_factor 0.2 на 45 000 строк мусор накапливается
--    долго, а stats_source_hourly обновляется по ON CONFLICT - там мусор
--    копится быстро. Scale factors опущены до 0.02 / 0.01 на листовых
--    партициях: партиционированная таблица их не принимает ("cannot
--    specify storage parameters for a partitioned table"), поэтому
--    настройка идёт по каждой партиции, включая созданные позже
--    PartitionMaintenance.

CREATE INDEX idx_source_error_log_source_created
    ON storm.source_error_log (source, created_at DESC);

DO $$
DECLARE
    parent_name TEXT;
    leaf_name  TEXT;
BEGIN
    FOREACH parent_name IN ARRAY ARRAY['request_log', 'source_error_log', 'stats_source_hourly', 'cached_result']
    LOOP
        FOR leaf_name IN
            SELECT c.relname
            FROM pg_class c
            JOIN pg_inherits i ON i.inhrelid = c.oid
            JOIN pg_class p ON p.oid = i.inhparent
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE p.relname = parent_name AND n.nspname = 'storm'
        LOOP
            EXECUTE format(
                'ALTER TABLE storm.%I SET (autovacuum_vacuum_scale_factor = 0.02,'
                ' autovacuum_analyze_scale_factor = 0.01)',
                leaf_name);
        END LOOP;
    END LOOP;
END $$;