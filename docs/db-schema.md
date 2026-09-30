# Схема базы StormEvent

Схема `storm`, PostgreSQL 16, миграции Flyway в
`storm.event/src/main/resources/db/migration`. Историю схемы ведёт
`flyway_schema_history` (в этой же схеме, `spring.flyway.default-schema=storm`).

| Миграция | Содержимое |
|---|---|
| `V1__schema.sql` | схема `storm`, 8 таблиц, CHECK-ограничения, партиционирование, DEFAULT-партиции |
| `V2__seed_source_state.sql` | пять источников, `ON CONFLICT DO NOTHING` |
| `V3__indexes.sql` | вторичные индексы |

V1 вынесен целиком и может быть переписан: до него ни одна миграция не была
применена ни к одной базе (ADR-040). V2 и V3 идут после — данные и индексы
не должны зависеть от того, в каком порядке правили DDL.

## Таблицы

### `storm.session` — диалог пользователя

| Колонка | Тип | Смысл |
|---|---|---|
| `id` | `uuid` PK | идентификатор диалога, приходит от клиента |
| `intent` | `jsonb` | распознанный интент: слоты, режим, уточнения |
| `last_access_at` | `timestamptz` | последнее обращение, двигается каждым запросом |
| `expires_at` | `timestamptz` | когда диалог считается истёкшим, двигается вместе с `last_access_at` |
| `created_at` | `timestamptz` | создание диалога |
| `ttl_seconds` | `int` CHECK `> 0` | окно жизни, задаёт приложение (ADR-038) |
| `state` | `varchar(16)` CHECK `NEW\|ACTIVE\|CLOSED` | состояние диалога |

TTL — 15 минут с последнего обращения (ADR-VL-15, ПРОТ-06/У-7).
`expires_at` хранится явно и продлевается тем же запросом, что и
`last_access_at`: вычислять его в БД нельзя — `timestamptz + interval`
зависит от таймзоны сессии и не иммутабелен, то есть не индексируется.
Проверка `SchemaConstraintTest.timestampsAreTimezoneAware` не даёт вернуться к
`timestamp without time zone`.

### `storm.session_message` — реплики диалога

`id bigint identity` PK, `session_id` FK `ON DELETE CASCADE`, `role`
(`user|agent`), `kind` (`search|refine|answer|system`), `text text`,
`request_id`, `created_at`. Отдельного refine нет: уточнения — обычные реплики
(ADR-VL-02), поэтому таблица растёт линейно с диалогом.

### `storm.source_state` — ручное состояние источников

`source varchar(32) COLLATE "C"` PK, `enabled`, `draining`, `ramp_until`,
`updated_at`. `enabled=false` — мгновенный стоп источника, `draining=true` —
новые запросы не отправляем, текущие доводятся (АНП-101/106). Отдельной
`circuit_state` нет (ADR-VL-04).

### `storm.cached_result` — кэш нормализованных ответов

`cache_key varchar(255)` PK, `source`, `domain`, `payload_json jsonb`,
`created_at`, `expires_at` (CHECK `expires_at > created_at`), `stale`.
`stale` — «протух, но отдавать можно, чтобы не молчать» (ADR-042).

### `storm.idempotency` — окно дедупликации

`request_id uuid` PK, `response_json jsonb`, `session_id` (nullable),
`created_at`, `expires_at`. Повторный `request_id` в окне 5 минут возвращает
прежний ответ вместо повторной работы (ADR-VL-15). Захват — `INSERT ... ON
CONFLICT DO NOTHING`: выигрывает ровно один поток.

### `storm.request_log` — аудит запросов

`id` + `created_at` в PK (партиционирование), `request_id`, `session_id` FK
`ON DELETE SET NULL`, `text text`, `intent_json jsonb`, `status`
(`OK|ERROR`), `duration_ms`, `created_at`. Полный текст промпта живёт здесь,
а не в `session.intent` — иначе jsonb раздувается (шаг 269–270 плана).

### `storm.source_error_log` — ошибки источников

`id` + `created_at` в PK, `request_id`, `source`, `code`, `message text`,
`latency_ms`, `created_at`. Пишется пачками при падении источника, служит
основой для агрегатов и алертов.

### `storm.stats_source_hourly` — агрегаты ошибок

`hour` + `source` + `code` в PK, `count CHECK >= 0`. Инкремент — один
`INSERT ... ON CONFLICT DO UPDATE SET count = count + 1`, без SELECT + UPDATE.
Часы в UTC (ADR-035): сутки для пользователя — Минск, агрегаты — UTC.

## Индексы (V3)

| Индекс | Зачем |
|---|---|
| `idx_session_expires_at` | чистка истёкших диалогов |
| `idx_session_state` | выборка диалогов по состоянию |
| `idx_session_message_session_created` | контекст диалога: последние 20 реплик |
| `idx_cached_result_expires_at` | чистка протухшего кэша |
| `idx_cached_result_source_domain` | статистика по источнику и домену |
| `idx_idempotency_expires_at` | чистка окна дедупликации |
| `idx_request_log_created_at` | выборка по времени, retention |
| `idx_request_log_session_id` | история сессии |
| `idx_source_error_log_created_at` | дашборд по времени |
| `idx_source_error_log_source_code_created` | частые ошибки конкретного источника |

Правило: колонка внешнего ключа всегда индексируется, если левая часть
индекса не покрыта первичным ключом (ADR-037). Проверяется не глазами, а
тестом `SchemaConstraintTest.everyForeignKeyHasSupportingIndex` по
`pg_constraint` и `pg_index`. Планы «горячих» запросов зафиксированы в
`IndexUsageTest` на 20 000 строк.

## Типы и соглашения

- snake_case, `bigint identity` для технических ключей, `uuid` для ссылок
  между сессиями и запросами, строковые естественные ключи для состояний
  и кэша (ADR-014, ADR-031).
- `COLLATE "C"` на `source`, `domain`, `code`: сравнение и сортировка без
  правил локали, регистр источников не важен.
- `text` для пользовательского и внешнего текста, `varchar(n)` для кодов и
  ключей.
- Технические метки — `timestamptz` (внутри UTC), отображение — Europe/Minsk
  (ADR-016).

## Чек-лист «не делать» (БД)

- **N+1.** Housekeeper читает пачкой `findExpired...` и удаляет одним
  `deleteByIds`/`deleteOlderThan`, а не «SELECT → DELETE» в цикле.
- **`DELETE ... LIMIT`.** В PostgreSQL такого синтаксиса нет: ограничение
  задаётся подзапросом по первичному ключу, иначе удаление неограниченно.
- **Запрос в цикле.** Батчи вместо поштучных `insert`.
- **Дублирование текста.** В `session.intent` лежит только интент, полный
  текст — в `request_log`.
- **Конкатенация пользовательского текста в SQL.** Только параметры;
  проверяется тестом на строке `'; DROP TABLE storm.request_log; --`.
- **Отдельный refine и его таблицы.** Уточнения — реплики диалога.
- **Индекс «на всякий случай».** Новый индекс добавляется вместе с
  запросом, который его использует, и с тестом плана.
