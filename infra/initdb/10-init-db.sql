-- Идемпотентный скрипт инициализации кластера StormEvent.
--
-- Выполняется официальным образом postgres при первом старте контейнера
-- (/docker-entrypoint-initdb.d) и повторно - тестом InitDbScriptTest, который
-- применяет файл дважды подряд и требует отсутствия ошибок.
--
-- Границы (ADR-044):
--   * здесь только то, что должно существовать до первой миграции Flyway -
--     расширения и схема;
--   * таблицы, индексы, настройки автовакуума - в миграциях, чтобы одна
--     миграция описывала одну идею и применялась одинаково в тестах;
--   * параметры сервера (checkpoint_timeout, log_min_duration_statement) -
--     в compose.yaml, они не выражаются SQL.
--
-- Файл исполняется и psql, и драйвером JDBC, поэтому здесь нет команд psql
-- (\connect, \gexec): только обычные SQL-операторы.

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE SCHEMA IF NOT EXISTS storm;

ALTER SCHEMA storm OWNER TO CURRENT_USER;

GRANT USAGE, CREATE ON SCHEMA storm TO CURRENT_USER;

REVOKE ALL ON SCHEMA storm FROM PUBLIC;