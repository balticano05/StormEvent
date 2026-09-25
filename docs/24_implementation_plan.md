# План реализации StormEvent (детальный, 1500+ шагов)

Исходники: `docs/21…23` (хэппи/анхэппи пути, спека ошибок, блюпринт). Доки — **не гарантия**: каждое допущение помечено шагом «проверить/валидировать». План идёт «сверху вниз»: сначала решения (в т.ч. выбор БД), потом структура, потом реализация, потом тесты.

**Все решения владельца по шагу 1 собраны в `25_contradictions.md`** (ПРОТ-01…27, разделы 0, 0.1, 0.2). План приведён в соответствие с ними: шаги, отменённые решениями, помечены `~~отменено~~` или `[ОТМЕНЕНО]`, а решения владельца — `[ВЛ]`.

**Журнал решений (АDR) — [`decisions.md`](decisions.md)**. Нумерация двухсерийная:
- `ADR-001…ADR-016` — базовые решения ФАЗЫ 0 (БД, доступ, миграции, очередь, клиенты, LLM, TZ);
- `ADR-VL-01…ADR-VL-14` — **решения владельца** из блока 40.1 (канон, спорить нельзя);
- `ADR-017…ADR-052`, встречающиеся внутри отдельных шагов, — черновики «на ходу», в реестр попадут при реализации (коллизия номеров с `ADR-VL-*` невозможна).

Условные обозначения:
- `[✓]` — action, после которой должен быть зелёный тест/компиляция.
- `[ПРОВ]` — проверка допущения (данные, живой источник, конфиг), план не гарантирует истинность доки.
- `[МЕТ]` — обязательная метрика для шага.
- `[АЛЕРТ]` — обязательный алерт для шага.
- `[ВЛ]` — решение владельца (источник: `25_contradictions.md`), спорить не нужно.
- `[ОТМЕНЕНО]` — шаг снят решением владельца, не выполняется.

---

## ФАЗА 0. Контекст и решения (шаги 1–40)

1. [✓] [ПРОВ] Прочитать все доки: README, 01–23. Выписать противоречия → **выполнено**: `25_contradictions.md` (27 противоречий, решения владельца).
2. [✓] [ПРОВ] Проверить актуальность `pom.xml`: Java 21, Spring Boot 4.1.1, OkHttp 5.5.0, Jsoup, Jackson 3 → **выполнено** (подтверждено; нет jdbc/flyway/postgres/actuator — Ф-5).
3. [✓] [ПРОВ] Проверить, какие интеграционные тесты сейчас «живые» и требуют сети → **выполнено**: `AtlasClientIntegrationTest`, `BzdClientIntegrationTest`, `TicketBusClientIntegrationTest`, `BelHotelClientIntegrationTest`, `TicketProServiceIntegrationTest` (Ф-6).
4. [✓] [ВЛ] Создать `docs/decisions.md` — журнал АДР (architectural decision records): дата, контекст, решение, последствия, статус, покрытые ПРОТ/У/В. → **выполнено**: базовые `ADR-001…016` + решения владельца `ADR-VL-01…014`.
5. Решение АДР-001: **база данных**. Выбрать **PostgreSQL 16** (реляционные данные: сессии, кэш, состояния, логи, метрики). [ВЛ] Подтверждено.
6. Обоснование АДР-001: нужен SQL (агрегации метрик, TTL-чистки, индексы, транзакции сессий); KV-хранилище не даёт запросов к метрикам.
7. Альтернативы АДР-001: H2 (только тесты), Redis (только кэш/сессии, не метрики) — зафиксировать «почему нет».
8. Решение АДР-002: **SQL-доступ**. Использовать **Spring Data JDBC + JdbcTemplate** (без JPA: сущности источников не энтити БД).
9. Обоснование АДР-002: доменная модель (Offer и сущности источников) отделена от таблиц; JPA-магия принесёт больше проблем.
10. Решение АДР-003: **миграции**. Flyway (`flyway-core`) как единственный инструмент изменения схемы.
11. Решение АДР-004: **тестовая БД**. H2 в PostgreSQL-режиме для юнит/интеграционных тестов; `Testcontainers` — опционально для pg-специфики.
12. Решение АДР-005: **кэш и сессии поверх Postgres** через таблицы (без внешних Redis-зависимостей на первом этапе). [ВЛ] Подтверждено.
13. Решение АДР-006: **очередь** — in-memory `ArrayBlockingQueue(100)`, **FIFO без приоритетов и старения**; таблица `request_log` для аудита. [ВЛ] ПРОТ-05, У-6.
14. Решение АДР-007: **схема пакетов** — по блюпринту 23 (gateway/normalize/orchestration/agent/cache/session/queue/scheduler/exception/web/handler/metrics; **circuit — исключить**, [ВЛ] ПРОТ-08/09).
15. Решение АДР-008: **HTTP-клиенты** — базовый `OkHttpClient` + `newBuilder()`-производные; отдельный клиент/пул для SSE; **несколько клиентов разрешены** ради параллелизма. [ВЛ] ПРОТ-03 (план 24:29 — «единственный клиент» отменён).
16. Решение АДР-009: **виртуальные потоки** для fan-out к источникам (Java 21 virtual threads), без executor-пулов на источник.
17. ~~Решение АДР-010: deadline-модель (default 15000, бюджет 90 %)~~ **[ОТМЕНЕНО]** [ВЛ] ПРОТ-01/02: общего обрыва нет — **ждём выполнения всех** источников; `deadlineMs` — advisory; таймауты per-source; LLM — агентный цикл (шаг 40.1).
18. Решение АДР-011: **request-id** генерируется на входе, пробивается через MDC и таблицы.
19. [✓] [ПРОВ] LLM-провайдер → **закрыто**: реальный LLM, **OpenRouter** (function calling), ключ будет позже; до ключа — fallback (У-4, В-9/В-14).
20. Решение АДР-012: LLM — за интерфейсом `LlmGateway`; провайдер — OpenRouter, конфиг из env `OPENROUTER_API_KEY`/`OPENROUTER_MODEL`. [ВЛ]
21. [✓] [ПРОВ] Домены МВП → **закрыто**: **все источники, у которых есть API** (`atlasbus`, `ticketbus`, `bzd`, `ticketpro`, `belhotel`). [ВЛ] У-5.
22. Решение АДР-013: порядок реализации фаз — БД → исключения → модель → гейтвеи → параллельность → tools → combiner → LLM → web → очередь/сессии/кэш → метрики → E2E. **Circuit breaker — не делаем.** [ВЛ]
23. Составить карту «каталог ошибок 1–235 → фаза/класс/тест» (пустая матрица, заполняется по фазам).
24. [✓] [ПРОВ] Наличие СУБД → **закрыто**: **Docker Compose** (`compose.yml`, `localhost:5432`). [ВЛ] В-3.
25. Создать ветку `plan/implementation` от текущей (или продолжить DOCS — по решению).
26. Создать в репо `docs/implementation/` для фасов-планов и чеклистов.
27. Составить список внешних переменных окружения (DB_URL, DB_USER, DB_PASSWORD) и завести `.env.example`.
28. Завести `.gitignore`: прописать `*.env`, локальные логи, `.idea` (проверить текущий).
29. [✓] [ПРОВ] Перепроверить, что `application.properties` — единственный источник okhttp-конфигов сейчас → **выполнено**: также `OkHttpProperties.java` (connect 5s, read/write 10s, **callTimeout 0**, пул 5) — Ф-3.
30. Спланировать тест-стратегию: unit (JUnit5 + JSON-фикстуры) + **интеграционные на живых API** (профиль `integration`, отдельный прогон); e2e через MockMvc. [ВЛ] У-8.
31. Добавить зависимости в pom: `spring-boot-starter-jdbc`, `postgresql` (runtime), `flyway-core`, `spring-boot-starter-actuator` (опц.), тест: `MockWebServer`, `H2`.
32. [ПРОВ] Проверить, что Jackson 3 (`tools.jackson`) совместим с `spring-boot-starter-jdbc`/flyway (отдельных конфликтов не вносит).
33. Решение АДР-014: именование таблиц snake_case, PK — `bigint identity` или UUID (для request_id/session_id).
34. Решение АДР-015: идентификаторы сущностей источников — строковые `source:kind:externalId`, в БД как `varchar(128)` с уникальным индексом.
35. Написать ADR-016: временные зоны — **Europe/Minsk для всех расчётов, стыковок, границ суток и отображения**; в БД `timestamptz` (технически UTC), API отдаёт `+03:00`. [ВЛ] ПРОТ-15.
36. Написать ADR-017 (черновик): валюта BynAmount — `numeric(12,2)` + `currency char(3)`, нормализация в приложении.
37. [ПРОВ] Проверить нагрузку: сколько запросов/час ожидается (для размера пула и TTL-политик).
38. Составить чеклист «нельзя»: никакой секрет в репо, никакого ath/bot-anti в логах, никаких `*ClientException` наружу из tools.
39. Зафиксировать определение done для фазы: компиляция + все тесты фазы зелёные + ADR записан.
40. Первый коммит плана: `docs/24_implementation_plan.md` + map в README.

### 40.1. Решения владельца, добавленные после разбора противоречий (`25_contradictions.md`)

40.1. [ВЛ] **ADR-VL-01 (текстовый контракт)**: ответ агента — **обычный текст** (`text/plain`), не JSON. Один эндпоинт `POST /api/v1/agent/chat` (`sessionId` + `text`) → строка ответа. `ResponseDto`/сериализация офферов/SSE — не делаем (У-1, В-7).
40.2. [ВЛ] **ADR-VL-02 (сессия-чат)**: сессия = диалог; таблица `session_message` (роль/текст/время/`requestId`); **TTL 15 минут** с последнего обращения; состояния `NEW|ACTIVE|EXPIRED` (без `CANCELLED`) (У-2, ПРОТ-06/07).
40.3. [ВЛ] **ADR-VL-03 (агентный цикл)**: LLM (OpenRouter, function calling) **сам выбирает инструменты-источники**, мы выполняем вызовы параллельно и **ждём все**, результаты возвращаем в LLM; контекст — вся сессия, максимум **20 сообщений** (`llm.contextWindow=20`); лимит раундов — **на уровне агента** (`AgentProperties.llm.maxToolRounds`, по умолчанию 2 + правило в системном промпте, жёсткой остановки в коде нет; позже так же настроим для n8n) (0.2, В-11/В-12/В-13).
40.4. [ВЛ] **ADR-VL-04 (circuit breaker отложен)**: состояния CLOSED/OPEN/HALF_OPEN, `circuit_state`, half-open пробы — **не делаем**. Вместо: per-source таймауты, alert по счётчику ошибок, ручной флаг `source_state.enabled` (ПРОТ-08/09/10/11/12).
40.5. [ВЛ] **ADR-VL-05 (очередь простая)**: `ArrayBlockingQueue(100)`, FIFO, 4–8 воркеров, переполнение → 503 + `Retry-After: 5`. Без приоритетов, aging, `pausedUntil`/cooldown (ПРОТ-05, ПРОТ-12, У-6).
40.6. [ВЛ] **ADR-VL-06 (наблюдаемость)**: Prometheus/actuator **не подключаем**; счётчики `MetricsCollector` + `AlertEvaluator` с логом WARN (ПРОТ-20/21).
40.7. [ВЛ] **ADR-VL-07 (ошибки источников)**: 403 → сообщение пользователю **без повторов**; 1 пользовательский запрос = 1 ошибка (retry не накапливается); упавший источник всегда упоминается в ответе (ПРОТ-04/10/11).
40.8. [ВЛ] **ADR-VL-08 (модель оффера)**: **цены и тарифы подробно**, остальное минимум — `from`, `to`, `date/time`, `price`+`currency`, `seats`, `tariffName`/`class`, `source`, `kind`, `url`/`externalId`. `attributes`/`baggage`/`documents`/`cancellation`/`transfers` — после MVP (ПРОТ-19, В-10).
40.9. [ВЛ] **ADR-VL-09 (отмена)**: отмена запроса пользователем **не делается** (У-7).
40.10. [ВЛ] **ADR-VL-10 (админка)**: `/api/v1/sources/**` и `/api/v1/diagnostics/**` — заголовок `X-Api-Key` из `STORM_ADMIN_KEY` + CORS-whitelist (ПРОТ-26).
40.11. [ВЛ] **ADR-VL-11 (анти-бот)**: обход не делаем — уважаем `robots.txt`, при анти-боте сообщение пользователю + alert, источник отключается вручную (ПРОТ-27).
40.12. [ВЛ] **ADR-VL-12 (очередь приоритетов в доках)**: `13:25-26`, `13:73`, `15:36-38`, `23:484-487` — помечаются устаревшими (АНП-83 не применима).
40.13. [ВЛ] **ADR-VL-13 (Jackson/ResponseStatus)**: фикс Jackson 2→3 аннотаций в `entity/atlas/*`; убрать `@ResponseStatus(BAD_GATEWAY)` (ПРОТ-23/24).
40.14. [ВЛ] **ADR-VL-14 (warmup в гейтвеях)**: `BzdService`/`BelHotelService` не заводим; warmup/retry в гейтвеях + `WarmupScheduler` (ПРОТ-25).
40.15. [ВЛ] Пересобрать `13`, `16`, `22`, `23` под эти решения (устаревшие формулировки вычеркнуть, ссылки на `25_contradictions.md`).

## ФАЗА 1. Каркас приложения (41–80)

41. Проверить `Application` — добавить `@EnableScheduling`, `@EnableTransactionManagement`.
42. Создать пул планировщика: bean `ThreadPoolTaskScheduler` (2 потока, именованные `sch-1..2`).
43. Создать `RequestIdFilter` (OncePerRequestFilter): читает `X-Request-Id` или генерит UUID, кладёт в MDC `requestId`.
44. Написать `RequestIdFilterTest` (unit): подстановка/генерация/проброс в атрибут запроса.
45. Создать `ErrorCode` (enum) по блюпринту 23 (16 кодов) + отображение HTTP-статуса.
46. Написать `ErrorCodeTest`: каждый код маппится в статус и в ключ i18n, нет дублей HTTP без категоризации.
47. Создать `ErrorResponse` (record: requestId, code, httpStatus, message, source, domain, partial, retryAfterMs).
48. Создать `StormException` (abstract) с полями errorCode, source, retryAfterMs.
49. Создать `GlobalExceptionHandler` (`@RestControllerAdvice`): обработчики StormException/ValidationException/Generic.
50. «Гарантия» хендлера: внутренний исключение никогда не уходит клиенту (лог + INTERNAL_ERROR).
51. Написать `GlobalExceptionHandlerTest`: матрица кодов → JSON.
52. Создать `WebProperties` (`@ConfigurationProperties(prefix="storm.web")`): defaultDeadlineMs, maxOffers, langs.
53. Прописать базовые свойства в `application.properties`.
54. Создать health-эндпоинты: `/api/v1/health/live`, `/ready`.
55. Написать тест health: live всегда 200, ready зависит от `queue.reject`.
56. Создать `XRequestIdMDCFilter` для параллельных потоков: проброс MDC в виртуальные потоки (setup в ParallelExecutor).
57. Написать тест проброса MDC между потоками (assert requestId в дочернем потоке).
58. Создать `ApiResponseEnvelope` (не обязательно) — если решим единый конверт; ADR-018 (черновик, не путать с ADR-VL-02).
59. Написать aspect-заглушку логирования входа/выхода контроллеров (время, requestId, код).
60. Добавить `spring-boot-starter-actuator` и включить `metrics` endpoint (опциональный push в лог).
61. Проверить, что actuator не светит данные наружу (manage endpoints для готовой откл. пока).
62. Написать сквозной тест-заглушку: `GET /health/ready` через `MockMvc`.
63. [ПРОВ] Собрать проект `mvn -q compile` — без ошибок.
64. [ПРОВ] Прогнать существующие тесты `mvn test` — зелёные до наших изменений.
65. Зафиксировать шаг 64 в журнал (baseline).
66. Настроить логирование: pattern с `[%X{requestId}]` в `logback-spring.xml`.
67. Написать конфиг логгера источников на уровне DEBUG под каталог `com.workspace.storm.event.gateway`.
68. Создать `AppConfig` дополнение: `ExecutorService` для параллельных задач (virtual-thread-per-task).
69. Создать тест NPE-безопасности: `ErrorResponse` собирается при null source/домене.
70. Создать `RequestContext` (requestId+deadline+lang), прокидывается через параметры (не ThreadLocal наружу).
71. ADR-019 (черновик): RequestContext передаётся явно (память о параллелизме), MDC — только для логов.
72. Написать юнит-тест RequestContext (deadline расчёт, дефолты).
73. Привязать `RequestIdFilter` к `/api/v1/**` (не ко всем путям).
74. Добавить CORS-конфигурацию (для фронта) — ограничить origin'ы.
75. Написать тест CORS.
76. [МЕТ] Метрика `http.requests{path,status}` — пока счётчик в лог.
77. [АЛЕРТ] Алерт-заглушка на `INTERNAL_ERROR` (лог WARN при >0).
78. Проверить graceful shutdown: Bean `ShutdownHook`, ожидание активных запросов 30 сек (АНП-111).
79. Написать интеграционный тест: инициализация context поднимается с H2.
80. `[✓]` Фаза 1 готова: компиляция, baseline-тесты, health, request-id, перехватчик.

## ФАЗА 2. БД: выбор и каркас (81–170)

81. [ПРОВ] Определить, где поднимется Postgres: Docker (`docker run -e POSTGRES_DB=stormevent ...`) или локальная установка.
82. Записать в `.env.example` строку подключения JDBC.
83. Добавить в pom: `spring-boot-starter-jdbc`, `org.postgresql:postgresql` (runtime), `org.flywaydb:flyway-core` (+ `flyway-database-postgresql`).
84. Добавить в тестовые deps: `com.h2database:h2` (PostgreSQL-режим), `com.squareup.okhttp3:mockwebserver`.
85. Создать `DbProperties` (`@ConfigurationProperties(prefix="spring.datasource")`) или использовать стандартные `spring.datasource.*`.
86. Настроить `application.properties`: datasource url/username/password из env (`${DB_URL}` и т.д.).
87. Настроить `application-test.properties`: H2 URL `jdbc:h2:mem:stormtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`.
88. Создать `spring.flyway.locations=classpath:db/migration`, autoconfigured.
89. Проверить подключение: `mvn -q spring-boot:run` + psql `\dt` — пустая схема.
90. Создать первый миграционный файл `V1__schema.sql` — см. фазу 3; сейчас создать пустой и прогнать.
91. Настроить пул соединений: HikariCP (идёт со starter-jdbc) — размер 10, `maxLifetime 30m`, `connectionTimeout 3s`.
92. Написать `DataSourceHealthIndicatorTest`: приложение стартует, health ready=true, БД доступна.
93. Включить `spring.sql.init.mode=never` (мигрируем только Flyway).
94. Настроить транзакционный `PlatformTransactionManager` (штатный).
95. Написать первый репозиторий-заглушку `SessionRowMapper` для проверки маппера.
96. ADR-020 (черновик): используем `NamedParameterJdbcTemplate` — безопасность от SQL-инъекций, именованные параметры.
97. Написать тест CRUD на H2 (insert/select/update/delete) — инфраструктура маппера работает.
98. [ПРОВ] Проверить H2-режим PostgreSQL: `MODE=PostgreSQL` поддерживает `bigint identity`, `timestamptz` (эмуляция).
99. Настроить `flyway.baseline-on-migrate=true` на случай существующей базы.
100. Создать `dataAccess/exceptions` обработка: `DataAccessException → INTERNAL_ERROR` в advice.
101. Написать тест: нарушение unique → код `DATA_CONSTRAINT` (новый ErrorCode) → пользователь получит понятное сообщение.
102. ADR-021 (черновик): все запросы к БД — через репозитории (`repository/` пакет), не в сервисах.
103. Создать пакет-структуру БД: `repository/`, `mapper/`, `entity/db/` (схемные энтити).
104. Создать `BaseEntity` для схемы (id, createdAt, updatedAt).
105. Написать `TimestampMapper` (timestamptz ↔ Instant) как общий утилитный класс.
106. Написать тест `TimestampMapperTest` (UTC-конверсия).
107. Настроить `default_schema` в конфиге (выбрать `public` или `storm`).
108. ADR-022 (черновик): выбранная схема БД — `storm` (изоляция от других приложений).
109. Создать миграцию V1 с созданием схемы `storm` и `search_path`.
110. Проверить на реальном Postgres (не H2) применение миграции.
111. Настроить ретрай-логику на `ConnectException` к БД в HikariCP (initializationFailTimeout=-1 до первого запроса).
112. Написать тест: при недоступной БД приложение стартует, health ready=false, а не падает.
113. [ПРОВ] Проверить, что Flyway авто-миграции не конфликтуют с `spring.jpa` (jpa не используется).
114. Настроить DDL для теста: H2 мигрируется теми же V-файлами.
115. Создать тест `FlywayMigrationTest`: все миграции применяются на H2 чисто.
116. Определить политику хранения JSON: `jsonb` для intent/response/payload.
117. Написать маппер JSON ↔ `JsonNode` (Jackson 3) в общем `JsonSupport`.
118. Написать тест `JsonSupportTest` (сериализация/десериализация, null-safe).
119. ADR-023 (черновик): JSON в jsonb, никогда не фильтруется в SQL — извлекается в приложении.
120. Спроектировать размеры полей (varchar длины): requestId 36, source 32, code 64, url 512, name 256.
121. Создать словарь-константу `ColumnSizes` (или комментарии в миграции) — единый источник.
122. Написать проверку миграции: `V1` не содержит конфликтов имён с зарезервированными словами SQL.
123. Настроить `spring.datasource.hikari.pool-name=storm-pool` для трассировки.
124. Написать метрику `db.connections.active` — через Hikari metrics bean.
125. [МЕТ] Метрика `db.query.duration_ms` (аспект на репозиториях) — добавить.
126. ADR-024 (черновик): аспект мониторинга репозиториев через `@Timed`-аналог (AOP), не меняет вызовы.
127. Написать тест `QueryTimingAspectTest` (длительность >0).
128. Решить вопрос миграции индексов: каждый индекс — отдельная миграция (V*__add_index_*) для истории.
129. Написать стиль миграций: имя файла `V<номер>__<описание>.sql`, раздел комментариев.
130. [ПРОВ] Проверить версию Postgres: `checkpoint`, `jsonb` доступны (>= 9.4 для jsonb, >= 10 для identity).
131. Настроить время ожидания транзакции (`spring.transaction.default-timeout=10s`).
132. Написать тест: длинная транзакция откатывается по таймауту (rollback). 
133. ADR-025 (черновик): транзакции только на уровне сессии/кэша/логов; чтение источников — без транзакций.
134. Настроить `@Transactional(readOnly=true)` для query-репозиториев.
135. Создать `PaginationSupport` (limit/offset) для списков в БД (см. лог-таблицы).
136. Написать тест `PaginationSupportTest`.
137. Спланировать схему бэкапа: pg_dump + ротация (шаг-документ, не реализация).
138. [ПРОВ] Проверить, что срок жизни H2-тестов не пересекается с `@SpringBootTest` (чистые контексты).
139. Написать общий `BaseRepositoryTest` (H2, подъём context, очистка таблиц между тестами).
140. Настроить очистку: `DELETE FROM` в `@BeforeEach` по таблицам (или TRUNCATE).
141. [МЕТ] Метрика `db.pool.wait_ms` — залогировать в MetricsCollector.
142. Создать `DbReadyCheck` для readiness health.
143. Написать тест: readiness = «БД доступна и миграции применены».
144. Решить: джобы housekeeper через Scheduler обращаются к БД в своей транзакции (читать отдельную транзакцию).
145. Написать `HousekeepingTransactionTemplate` (programmatic transaction) для фоновых задач.
146. ADR-026 (черновик): фоновые джобы НЕ держат транзакцию во время сетевых вызовов источников.
147. Проверить конфигурацию Hikari `validationTimeout`, `leakDetectionThreshold`.
148. Настроить `leakDetectionThreshold=30s` в dev-профиле.
149. Написать тест: пул не утекает (после 100 операций active==0).
150. Создать `SchemaInfoEndpoint` (сколько миграций применено) для диагностики.
151. [МЕТ] Метрика `db.migrations.applied`.
152. Проверить autovacuum-конфиг Postgres для таблиц логов (частая запись) — см. доку.
153. ADR-027 (черновик): таблицы логов партицируются по времени (V-миграции позже, фаза 4).
154. Проверить в H2 партиционирование-не-использование (H2 не поддерживает) — тесты-заглушки.
155. [ПРОВ] Прогнать `mvn test` — зелёные (H2).
156. [ПРОВ] Прогнать приложение с реальным Postgres (Docker) — миграции применяются.
157. Зафиксировать в README (docs) раздел «Локальный запуск БД».
158. Обновить README-карту доков: добавить `24_implementation_plan.md`.
159. Коммит фазы: «ADD: база данных — схема 0, каркас, миграции».
160. [ПРОВ] Проверить, что секреты не попали в коммит (git status/diff).
161. Составить журнал известных отклонений от доки (например, если H2 не тянет jsonb — заменить на TEXT в тестах).
162. ADR-028 (черновик): если jsonb мешает H2, то в тестах маппинг через `JsonNode` из TEXT — фикс-маршрут.
163. Написать тест: H2 с jsonb-фикцией (использовать `CLOB`-колонку, если не поддержан).
164. [МЕТ] Метрика `db.query.error{repo}` — на каждую ошибку SQL.
165. [АЛЕРТ] Алерт-заглушка: дубли `insufficient` (нет migration) — лог ERROR.
166. Создать документацию тест-стратегии БД: H2 быстрые, Docker pg для приёмочных.
167. Проверить `spring.profiles.active=dev|test|prod`.
168. Настроить prod-профиль: только Postgres, H2 недоступен (fail-fast).
169. Написать `ProdProfileTest` (только в CI-среде с pg).
170. `[✓]` Фаза 2 готова: подключение, миграции, пул, тесты на H2 и pg, метрики.

## ФАЗА 3. БД: структурные таблицы, ключи, индексы (171–300)

171. Спроектировать ER-модель (текстовая): session, **session_message**, request_log, source_state, cached_result, idempotency, source_error_log, stats_hourly. ~~circuit_state~~ — [ВЛ] не делаем (ADR-VL-04).
172. Нарисовать диаграмму в `docs/db-er.md` (ascii или mermaid).
173. `V1` таблица `storm.session`: id uuid PK, intent jsonb, lastAccessAt timestamptz, createdAt timestamptz, ttlSeconds int, state varchar(16).
174. `session` правила: `lastAccessAt` обновляется каждым обращением; TTL чистка по `createdAt + ttl`.
175. Индекс `session(createdAt)` для housekeeper; `session(state)` для внешних.
176. `V2` таблица `session_message`: id bigint identity PK, sessionId fk → session.id (on delete cascade), role varchar(8) (`user`|`agent`), kind varchar(16) (`search`|`refine`|`answer`|`system`), text text, requestId uuid, createdAt timestamptz. [ВЛ] ADR-VL-02 — **диалог вместо `session_refine`**.
177. Индекс `session_message(sessionId, createdAt)` — выборка контекста для LLM (последние 20).
178. ADR-029 (черновик): FK `ON DELETE CASCADE` для дочерних записей сессии.
179. `V3` таблица `source_state`: source varchar(32) PK, enabled boolean, draining boolean, rampUntil timestamptz, updatedAt timestamptz.
180. Правило: `draining=true` ⇒ новые запросы не идут; `enabled=false` ⇒ мгновенный стоп (АНП-106/101).
181. ~~`V4` таблица `circuit_state`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04: circuit breaker не делаем.
182. ~~Индексировать `circuit_state(state)`~~ — **[ОТМЕНЕНО]** вместе с 181.
183. ~~ADR-030 (черновик): circuit-state в БД~~ — **[ОТМЕНЕНО]**; метрики ошибок источников идут в `source_error_log`/`stats_source_hourly`.
184. `V5` таблица `cached_result`: cacheKey varchar(255) PK, source varchar(32), domain varchar(16), payloadJson jsonb, createdAt timestamptz, expiresAt timestamptz, stale boolean.
185. Индекс `cached_result(expiresAt)` — housekeeper чистит протухшее.
186. Индекс `cached_result(source, domain)` — для статистики.
187. `V6` таблица `idempotency`: requestId uuid PK, responseJson jsonb, sessionId uuid nullable, createdAt timestamptz, expiresAt timestamptz.
188. Правило идемпотентности: повторный `requestId` из окна → вернуть прежний ответ (см. шаг 43).
189. Индекс `idempotency(expiresAt)` — чистка.
190. `V7` таблица `request_log`: id bigint identity PK, requestId uuid, sessionId uuid, text varchar(2000), intentJson jsonb, status varchar(16), durationMs int, createdAt timestamptz.
191. Индекс `request_log(createdAt)` — партиционирование по времени (фаза 4).
192. Индекс `request_log(sessionId)` — история сессии.
193. `V8` таблица `source_error_log`: id bigint identity PK, requestId uuid, source varchar(32), code varchar(64), message varchar(1000), latencyMs int, createdAt timestamptz.
194. Индекс `source_error_log(createdAt)` для дашборда/агрегации.
195. Индекс `source_error_log(source, code)` для алертов по кодам.
196. `V9` таблица `stats_source_hourly`: hour timestamptz, source varchar(32), code varchar(64), count int, PK (hour, source, code).
197. Правило: агрегация раз в час из request_log/source_error_log (см. фазу 15).
198. `V10` — отдельная миграция `GRANT`/роли (если нужно) — отложить.
199. Сформулировать типы ключей: requestId/sessionId — UUID; source_state/cache — строковые natural keys. ~~circuit_state~~ — [ВЛ] не делаем.
200. ADR-031 (черновик): natural keys для «состояний источника» (source name) — без суррогатных id.
201. Проверить уникальность `offerId` не храним в БД (офферы транзитные) — принять.
202. ADR-032 (черновик): офферы и комбо НЕ персистятся (вычисляются на лету), только сессия/логи/кэш.
203. Написать миграции V1–V9 одним PR (рефакторинг безопасности).
204. Написать `FlywayMigrationTest`: применить все V, проверить таблицы `information_schema`.
205. Написать мапперы схемы: `SessionMapper`, `SessionMessageMapper`, `SourceStateMapper`, `CachedResultMapper`. ~~`CircuitStateMapper`~~ — [ВЛ] не делаем.
206. Написать юнит-тесты мапперов (кортеж → объект, null-поля).
207. Создать репозитории: `SessionRepository`, `SessionMessageRepository`, `SourceStateRepository`, `CacheRepository`, `IdempotencyRepository`, `RequestLogRepository`, `SourceErrorLogRepository`, `StatsRepository`. ~~`CircuitStateRepository`~~ — [ВЛ] не делаем.
208. Каждый репозиторий: интерфейс + impl на `NamedParameterJdbcTemplate`.
209. `SessionRepository` методы: `insert`, `get`, `updateIntent`, `touch`, `delete`, `findExpired`.
210. Написать тест `SessionRepositoryTest` (H2): CRUD, TTL-выборка.
211. `SourceStateRepository`: `upsert`, `get`, `findByEnabled`.
212. Написать тест `SourceStateRepositoryTest`: upsert-idempotent.
213. ~~`CircuitStateRepository`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
214. ~~Написать тест `CircuitStateRepositoryTest`~~ — **[ОТМЕНЕНО]** вместе с 213.
215. `CacheRepository`: `get`, `put`, `findExpired`, `delete`.
216. Написать тест `CacheRepositoryTest` (stale-переходы).
217. `IdempotencyRepository`: `get`, `putIfAbsent`, `findExpired`.
218. Написать тест идемпотентности: одинаковый requestId → один ответ.
219. `RequestLogRepository`: `insert` (batch-friendly).
220. `SourceErrorLogRepository`: `insert` + `countSince(source, code, minutes)`.
221. `StatsRepository`: `incrementHourly`, `selectTopErrors`.
222. Написать тест `StatsRepositoryTest`: агрегация корректна за час.
223. Оптимизация: `batch insert` для source_error_log (jdbc batching, `NamedParameterJdbcTemplate.batchUpdate`).
224. Написать бенчмарк-тест (грубый): 10k батч-инсертов за <N мс (необязательный порог, метка).
225. Оптимизация: `prepared statement cache` (pg `jdbc.prepared_statement_cache_size=256`) — параметр url.
226. Оптимизация: `rewriteBatchedStatements=true` в JDBC URL для postgres.
227. [ПРОВ] Проверить H2-поддержку rewriteBatchedStatements — не нужна, только pg.
228. Написать `QueryIndexPlanTest`: `EXPLAIN` на «горячих» запросах (catched_result by expiresAt и т.п.).
229. [ПРОВ] Cпустить explain на реальном pg: убедиться, что индексы используются (SeqScan → IndexScan).
230. Оптимизация `request_log`: партиционирование по `createdAt` (native Pg partitioning, step 153).
231. Создать миграцию `V11__partition_request_log.sql` с default-партицией.
232. Написать тест-фолбэк: H2 без партиций (обычная таблица), запросы те же — абстракция в репозитории.
233. ADR-033 (черновик): партиционирование только на prod-pg через заданную миграцию; тесты используют полную таблицу.
234. Оптимизация `source_error_log`: тоже партиционировать по месяц (V12), retention 90 дней.
235. Оптимизация `session` TTL: housekeeper удаляет пачками по 1000 (limit) в транзакции.
236. Написать тест `TtlCleanupTest`: 100 expired + 10 живых → удалены только expired.
237. Оптимизация `idempotency`: чистить раз в минуту, batch delete by expiresAt.
238. Написать тест дедупликации на уровне репозитория.
239. Профиль индексов: уникальные — session(id-app), source_state(source), cached_result(cacheKey).
240. Описание составных индексов: `source_error_log(source, code, createdAt)` — для алертов по частоте.
241. ~~Описание partial index `WHERE state='OPEN'` на circuit_state~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
242. ~~Создать миграцию V13 с partial index (pg-only)~~ — **[ОТМЕНЕНО]** вместе с 241.
243. [ПРОВ] Проверить на pg hв high-level план: partial index не пессимизирует H2-тесты.
244. Полнотекст: пока не нужен (текст промпта не ищем) — ADR-034 отказ от pg_trgm.
245. Валидировать нормализацию: все `varchar` с кодировкой utf8, `COLLATE "C"` где регистр не важен (id).
246. Выбрать collation для `source`, `code` — `C` (ускорение сортировки) — миграция V14.
247. [ПРОВ] Проверить/задокументировать влияние collation на H2-тестах.
248. Написать тест: collation 'C' не влияет на равенство для латиницы и цифр.
249. Спроектировать партиционную стратегию `stats_source_hourly` — PK (hour, source, code) в пределах месяца (V15 — pg-only, H2 fallback).
250. Написать тест на границы месяца (последний час старого месяца).
251. ADR-035 (черновик): границы агрегации — UTC-часы.
252. Создать `EntityScan`/`@MapperScan` (Spring) для репозиториев.
253. Написать конфиг транзакций `@EnableTransactionManagement` и продемонстрировать rollback в тесте.
254. Написать тест: insert session + refine в одной транзакции, сбой → откат обоих.
255. Оптимизация одновременных сессий: `SELECT ... FOR UPDATE` на session при refine (конфликт параллельных refine).
256. Написать тест параллельного refine: два потока на одну сессию → сериализация (или один получает SESSION_EXPIRED).
257. ADR-036 (черновик): pessimistic locking на session при refine (короткая транзакция).
258. Оптимизация чтений кэша: `allowUpdate` vs `SELECT FOR UPDATE` — не нужен `SELECT FOR UPDATE` для read-only кэша (in-place optimistic).
259. Написать тест cache race: два потока пишут одинаковый ключ → без дублей и гонки.
260. Оптимизация метрик: `stats_source_hourly` инкремент `INSERT ... ON CONFLICT DO UPDATE`.
261. Написать тест upsert-агрегации на H2 (PostgreSQL-режим поддерживает ON CONFLICT).
262. Проверить default-значения: `enabled=true` для всех источников в `V3` (или в коде при первом чтении).
263. Написать тест дефолтов БД (source_state дефолт enabled=true).
264. Защита от «мёртвых» записей: `updatedAt` в session обновляется при touch (для миграций и housekeeper).
265. Написать тест: `touch` не трогает intent, обновляет lastAccessAt.
266. SQL-безопасность: проверить, что свободный текст (text промпта) не участвует в SQL (параметры).
267. Проверить N+1: housekeeper читает `findExpired` и удаляет пачкой (не в цикле select→delete).
268. Написать тест: housekeeper выполняет 1 SELECT + 1 DELETE batch.
269. Оптимизация размера jsonb: при больших intent — не хранить весь текст в session дублированно.
270. Решение: в session хранить только intent, полный текст — в request_log.
271. Документировать схему в `docs/db-schema.md` (таблицы, колонки, индексы, объяснение).
272. Написать проверку «нет миграции без индекса для FK-колонки» (если в SELECT участвует).
273. Миграция: FK на session_refine.sessionId → нужен индекс (PK composite даёт prefix — проверить).
274. Миграция: FK на request_log.sessionId → индекс есть (шаг 192).
275. ADR-037 (черновик): FK-колонки всегда индексируются, если левая часть не покрыта PK.
276. Написать тест `ForeignKeyIndexCheck` — линтер миграций (SQL разборчик простой).
277. Проверить правила СУБД для `boolean`: `enabled`/`draining`/`stale`/`partial` — `boolean` или `smallint` (выбрать boolean).
278. [ПРОВ] Убедиться, что `partial` из SSE-результатов не путается с `stale` из кэша — разные колонки.
279. Оптимизация строк: `text` для полей описаний/сообщений, `varchar` для кодов.
280. Написать тест вставки длинного сообщения (1000 символов) — не режется.
281. Тест на SQL-инъекцию: `message='; DROP TABLE...` сохраняется как данные (юнит-тест репозитория).
282. Создать хранимую политику вычисления TTL: `session.ttlSeconds` задаётся приложением (не в БД-функции).
283. ADR-038 (черновик): TTL в приложении (простота), БД только хранит значение.
284. Установить дефолт TTL: сессия активна **15 минут** с последнего обращения (продлевается каждым запросом), `state` без `CANCELLED`. [ВЛ] ПРОТ-06/У-7.
285. Прописать в `SessionProperties` (`sessionTtlMinutes=15`).
286. Написать тест расчёта TTL (активный/идл).
287. Оптимизация `idempotency`: TTL 5 минут (окно дедупликации одного ручного ретрая).
288. Прописать `IdempotencyProperties`.
289. Спланировать рост: `stats_source_hourly` retention 3 месяца, `request_log` 30 дней, `source_error_log` 90 дней.
290. Написать документ «ретеншн и чистки» в `docs/db-retention.md`.
291. Настроить `SessionHousekeeper` шаг: удаление сессий сверх TTL — см. фазу 14.
292. [ПРОВ] Прогнать миграции на pg чисто (V1–V15) без warning-ов.
293. Прогнать `mvn test` — зелёные.
294. Коммит «ADD: schema базы — сессии, кэш, состояние, логи, метрики, индексы».
295. Обновить metrics: `db.tables.count`, `db.indexes.count` в ready.
296. Обновить `docs/decisions.md` — ADR 28–38.
297. Проверить `git diff` на SQL-инъекции/секреты.
298. Написать чек-лист «не делать» для БД: никаких N+1, SELECT * из jsonb, запросов в цикле.
299. Прогнать линтер миграций (если настроим simple script в `scripts/check_migrations.sh`).
300. `[✓]` Фаза 3 готова: схема, ключи, индексы, репозитории, оптимизации (batch/partial index/partition/upsert) — тесты.

## ФАЗА 4. БД: оптимизация запросов и эксплуатация (301–390)

301. [ПРОВ] Снять `EXPLAIN ANALYZE` на всех «горячих» запросах фазы 3 — фиксируем baseline (мс, rows).
302. ADR-039 (черновик): целевой budget: поиск сессии <5 мс, upsert кэша <10 мс, housekeeper <100 мс пачкой.
303. Настроить `pg_stat_statements` в Docker-конфиге для наблюдения топ-запросов.
304. Написать страницу диагностики `/api/v1/diagnostics/slow-queries` (read-only, админ).
305. Оптимизация: использование `EXISTS` вместо `IN` в housekeeper-условиях.
306. Оптимизация: `ORDER BY ... LIMIT` с корректным индексом для «старых записей».
307. Оптимизация: `INSERT ... RETURNING id` для request_log (получение id без второго запроса).
308. Написать тест: batch-вставка request_log возвращает корректные id.
309. Оптимизация кэша: `SELECT ... WHERE cacheKey AND expiresAt > now()` — покрыть составным индексом (индекс шага 185 уже).
310. Оптимизация метрик: агрегация `countSince` с индексом (source, code, createdAt).
311. Периодич: housekeeper-чистки выполняются ночью (офф-пик) через `QueueMonitor`-окно (optional).
312. Замер: прогон load-теста на 500 сессий + 5000 логов на pg — фиксируем профили.
313. Настроить `autovacuum_vacuum_scale_factor` — под таблицы с высокой записью (логи).
314. Настройка `checkpoint_timeout=15min` для pg (уменьшение I/O-пика).
315. document: конфиг докера прогнать через `docker-entrypoint-initdb.d` (инициализация).
316. ADR-040 (черновик): конфиг БД управляется через миграции + переменные докера, без ручного ALTER.
317. Написать `scripts/init-db.sql` (создание роли/БД/schema) — idempotent.
318. Написать тест приёмочный: `scripts/init-db.sql` применяется в Docker 2 раза без ошибок.
319. Оптимизация коннектов: пул sized = (cores*2)+1, минимально 10.
320. Проверить `idleTimeout=10m` для освобождения простаивающих коннектов.
321. Настроить `minimumIdle`=2 (не греем пул).
322. Написать тест: после 50 параллельных запросов HMS-пул не растёт без причины (assert max<=N).
323. Оптимизация транзакций: `session`-обновления — `readCommitted` + короткие транзакции.
324. ADR-041 (черновик): iso-level read committed; SET `default_transaction_isolation` не меняем.
325. Проверить deadlock-случаи: два потока обновляют `session` — тест-проверка.
326. ~~Написать тест: параллельные `upsert` circuit_state~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
327. Оптимизация ошибок: `source_error_log` вставка в fire-and-forget с `try/catch` (не роняет поиск).
328. Написать тест: при недоступной лог-таблице поиск продолжается (основной поток не падает).
329. ~~Настроить `metrics` вывод в Prometheus-формате (actuator + micrometer-registry-prometheus)~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-06: метрики своими счётчиками + `AlertEvaluator` с логом WARN; вернуться отдельным решением.
330. Написать тест: готовность метрик после запросов.
331. [АЛЕРТ] Алерт `db.query.errors > 5/мин` — лог ERROR.
332. [АЛЕРТ] Алерт `db.pool.wait_ms > 200` медиана — WARN.
333. Настроить `logback` отдельный файл-аппендер `storm-db.log` (только SQL-сообщения).
334. Включить `logging.level.org.springframework.jdbc=DEBUG` в dev-профиле (не prod).
335. Написать тест-metrics: количество запросов к БД за один поиск (счётчик).
336. Оптимизация полей: `int` вместо `bigint` для durationMs, counters.
337. Валидировать типы: `BigDecimal` для цен в jsonb — без double.
338. Написать тест: хранение цены 25.50 в кэше не теряет точность.
339. Оптимизация кэша: не храним источники-офферы целиком — только нормализованный payload (мал).
340. ADR-042 (черновик): кэш — нормализованные офферы (jsonb), не сырые HTML.
341. Написать нагрузочный мини-тест: 200 чтений кэша параллельно.
342. Проверить pg: `jsonb` не индексируется вслепую — индексы только на scalar-колонки.
343. Оптимизация: `GIN`-индекс НЕ добавляем (нет jsonb-поиска).
344. Итог «hot queries» — документ `docs/db-hot-queries.md` с explain-планами.
345. Настроить `log_min_duration_statement=100ms` в dev, чтобы видеть медленные запросы.
346. Написать тест: ни один репозиторный запрос не попадает в «медленные» (сэмпл).
347. Оптимизация чистки: housekeeper `DELETE ... WHERE id IN (SELECT ... LIMIT 1000)` батчами.
348. Написать тест пачечной чистки: 5000 записей чистятся ≤ 6 операций.
349. Проверить, что housekeeper не блокирует пользовательские транзакции (ROW-lock vs table-lock).
350. ADR-043 (черновик): housekeeper использует `SELECT ... FOR UPDATE SKIP LOCKED` на малых пачках.
351. Написать тест `SkipLockedTest`: два housekeeper-воркера не дерутся.
352. Настроить ретеншн логов: job удаляет >90 дней, через `partition drop` (pg) в идеале.
353. Миграция V16: партии `request_log` by month на pg; `DROP PARTITION` job.
354. Написать тест: партиционные имена согласованы с `createdAt`.
355. [ПРОВ] Прогнать E2E на pg (Docker), затем H2 — поведение идентично.
356. Оптимизация idempotency: `ON CONFLICT DO NOTHING` + повторное чтение — без гонок.
357. Написать тест гонки идемпотентности: 10 параллельных POST одного requestId → 1 успешный объект.
358. Обновить документацию дб-схемы (V16 map).
359. [МЕТ] Метрика `db.statement.count{type=select|insert|update|delete}` (через аспект).
360. [МЕТ] Метрика `db.rows.read` суммарно.
361. Настроить `MetricsCollector` приём метрик из аспекта (не храним, просто счётчики).
362. Оптимизация: пул для housekeeper отдельный (1 поток, свой DataSource?) — нет, общий, но с изоляцией транзакций.
363. Проверить `spring.datasource.hikari.connection-init-sql` не нужен.
364. Валидировать, что `jsonb` payload из старой версии (без поля) десериализуется (маппер с `ignoreUnknown`).
365. Написать тест миграции данных: старая сессия без `intent` → дефолт.
366. Оптимизация backfill-фикстур: в тестах лёгкие JSON-фикстуры (не 50МБ).
367. [ПРОВ] Довести Docker Compose: `compose.yml` с pg, порт, vol, healthcheck.
368. Написать тест: приложение дожидается здоровья БД перед стартом (не гонка).
369. Проверить в readiness `db migrations applied == ожидаемое число`.
370. [АЛЕРТ] Алерт «миграции не применены / не сходятся» — лог ERROR + ready=false.
371. Оптимизация: кэш города (suggest) обновляется из справочников — см. CacheRefreshScheduler (фаза 14), но поле — cached_result.
372. Написать тест: `CachedResult` помечается stale при expiresAt < now.
373. ~~Оптимизация частых чтений circuit_state: in-memory копия + синк раз в 5 сек~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
374. ~~Написать тест синхронизации circuit~~ — **[ОТМЕНЕНО]** вместе с 373.
375. ~~ADR-044: circuit_state в БД, чтения из памяти~~ — **[ОТМЕНЕНО]** вместе с 373.
376. ~~Проверить согласованность N экземпляров (circuit в памяти)~~ — **[ОТМЕНЕНО]** вместе с 373.
377. ADR-045 (черновик): архитектура МВП single-node; multi-node — в будущем (БД остаётся источником истины).
378. ~~Метрика `source.circuit.memory.drift`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
379. [ПРОВ] Stress-тест БД: 50 потоков × 100 операций (смесь session/cache/log) — без ошибок.
380. Записать результаты stress-теста в `docs/db-load-results.md`.
381. Оптимизация строки подключения: `sslmode=require` в проде (проперти).
382. Возможность скрыть пароли: `DB_PASSWORD` через env, не в файле.
383. Проверить: `.env` не попадает в git (шаг 28).
384. Валидация журнала: каждый ADR 28–45 существует и мотивирован.
385. Прогнать полный `mvn test` + migrate на pg чисто.
386. Опция: создать миграцию с data-seed (демо-сессия) для ручного теста.
387. Написать тест seed-миграции (идемпотентность).
388. `[✓]` Фаза 4 готова: оптимизации запросов, бенчмарки, партиции, эксплуатация, метрики БД.
389. Обновить README-карту: 24-й файл.
390. Коммит фазы: «UPDATE: оптимизация БД — explain, batch, partition, stats, housekeeper».

## ФАЗА 5. Слой ошибок и перехват (391–470)

391. Создать `exception/` завершённую иерархию по блюпринту 23 (StormException root).
392. Перенести существующие `*ClientException/*ServiceException` под новый базовый класс (compile-safe).
393. Убрать `@ResponseStatus` с `*ClientException/*ServiceException` (статус задаёт advice).
394. Проверить: существующие тесты, где ожидается исключение — не сломались.
395. Создать `ParseException(message, partialData)`: любая из парсинг ошибок каталога (37–70).
396. Адаптировать существующие парсеры: вместо `IllegalArgumentException` на пустом SSE — `ParseException`.
397. Написать юнит-тест: парсер на пустоту → ParseException с partial-null.
398. Создать `ToolException` (верхний уровень инструмента): несёт `ToolResult`-обёртку.
399. Создать `LlmException` (с носителем ErrorCode LLM_UNAVAILABLE).
400. Расширить `ErrorCode` кодами `DATA_CONSTRAINT`, `SEARCH_FINISHED_PARTIAL` (если нужно), `PROVIDER_DOWN`.
401. Написать `ErrorCodeTest`: полный маппинг code → httpStatus → messageKey (100% покрытие кодов).
402. Построить карту «исключение → ErrorCode» в `ExceptionRegistry` (single map, без switch-дублирования).
403. Написать тест registry: каждое ключевое исключение вернёт ожидаемый код.
404. Дополнить advice: `DataAccessException`, `ValidationException`, `ParseException`, `TimeoutException`.
405. Проверить порядок `@ExceptionHandler`: специфичные раньше общих.
406. Написать тест advice: очередь всех кодов → корректный JSON.
407. Проверить, что advice не перехватывает исключения пограничных потоков (параллельные executor) — они не пробрасываются (см. фаза 8).
408. Завести `SourceFallbackHandler` (анхэппи логика «один источник упал» → пусто + warning).
409. Написать тест: fallback-хендлер собирает warnings из всех sourceResults.
410. Реализовать `Retry-After`-обработку: парсить из 503/429, класть в StormException.retryAfterMs.
411. Написать тест: 503 с Retry-After→retryAfterMs установлен, max 5000.
412. Написать `ErrorResponseBuilder` (одна точка построения JSON).
413. Написать тест билдера: null-safe, все поля.
414. Убедиться, что `message` берётся из i18n (сообщения пользователю) всегда.
415. Сделать `messages.properties` + `_en` (каталог фазы 16 — заготовка).
416. Конвертер кодов i18n: `MessageResolver` (простой enum+properties).
417. Написать тест резолвера: fallback на ru при отсутствии en.
418. Добавить запрет «никогда не раскрывать внутренний стек-Трейс» (тест: нет java.util в message).
419. Написать тест: message не содержит class-путей исключения.
420. [МЕТ] Каждая ошибка инкрементирует метрику `errors{code,source}` через аспект advice.
421. [АЛЕРТ] `parse.errors > 10/мин` — порог в AlertEvaluator (заготовка фазы 15).
422. Логирование: в advice логируем stack trace с requestId (WARN/ERROR).
423. Написать тест: MDC-log содержит requestId.
424. Убедиться: `ParseException` с partial передаёт `partial=true` в ответ.
425. Написать тест: парал-ответ от ParseException частичен.
426. Проверить, что `INTERNAL_ERROR` не рекуррентно логирует (защита от зацикливания).
427. Написать тест устойчивости advice к exception внутри хендлера (try/catch вокруг логирования).
428. Надстроить `messageResolver` для suggestion-подсказок Clarifier (см. фаза 12).
429. ADR-046 (черновик): user-messages хранятся в i18n (ru/en), коды стабильны.
430. Написать smoke-тест: любое исключение из каталога 1–235 → JSON без 500-стогов.
431. Выполнить «проход каталога»: для каждой ошибки 1–235 отметить класс исключения, который её ловит (таблица).
432. Пустой проход: то, что не покрыто, попадает в список «TO-DO фазы 16».
433. Проверить совместимость старых `*ServiceException` конструкторов (message, cause) — сохранить.
434. Проверить, что `AtlasService.search` теперь пробрасывает StormException с source=atlas.
435. Написать тест сервисного слоя: каждая ошибка источника → инструмент → PrincipalResult, не выходит наружу.
436. Валидация: `@Valid` в контроллерах кидает MethodArgumentNotValid → BAD_REQUEST (400, поленые сообщения).
437. Написать тест: invalid body → 400 с полем/сообщением валидатора.
438. [ПРОВ] Прогнать все существующие тесты проекта (регрессия после переноса исключений).
439. Прогнать код-стиль: `mvn compile`+lint (checkstyle если настроен).
440. Обновить `docs/07_unhappy_llm.md` привязка к LlmException — ADR/комментарий.
441. Дополнить `docs/decisions.md` — ADR-046.
442. Коммит «UPDATE: система исключений и перехват, i18n, маппинг кодов».
443. `[✓]` Фаза 5 готова.
444. Проверить покрытие каталога: таблица «ошибка→класс→тест» начата (продолжится в 16).
445. Зафиксировать know-issues: Retry-After для 429/503 (раньше — в клиентах).
446. Обновить `docs/22_spec_errors.md` карту классов (после стабилизации).

## ФАЗА 6. Unified-модель и нормализация (447–530)

447. Создать `dto/offer/`: `Offer`, `Price`, `GeoPoint`, `TransportOffer`, `EventOffer`, `HotelOffer`.
448. `Offer` — record/класс по блюпринту 23 (id, domain, source, from/to, departure/arrival, price, seats, link, attributes).
449. `Price` — record `{BigDecimal amount, Currency currency, BigDecimal amountByn}`.
450. `GeoPoint` — `{double lat, double lon}`.
451. `TransportOffer` доп. поля: busType/carrier/trainNumber/class.
452. `EventOffer` доп. поля: venue, category, images, startDate.
453. `HotelOffer` доп. поля: roomType, capacity, stars, breakfastIncluded, bookingUrl.
454. Создать `OfferIdFactory`: `source:kind:externalKey`.
455. Написать тест: id уникален для разных источников, стабилен для одинаковых.
456. Создать `OfferNormalizer` (обработка «грязных» цен/дат, каталог 63–70, 109–110, 140–142).
457. Реализовать нормализацию цены: из строки («25.50 BYN») → BigDecimal, из диапазона («от 25») → min.
458. Нормализация копеек BZD: price/100 (когда признак копеек известен по источнику).
459. Нормализация валют: BYN/RUB/USD → `amountByn` (курсы в Constants, затычка 1:1 с пометкой).
460. ADR-047 (черновик): курс руб/долл в Constants как первичная метрика, обновляется вручную + `currency.unknown` флаг.
461. Нормализация дат: ISO → Instant, поддержка 12h/24h/locale/relative (завтра) через `DateParser`.
462. Создать `DateParser` с паттернами: `dd.MM.yyyy HH:mm`, `HH:mm`, AM/PM, «сегодня/завтра», TZ Europe/Minsk.
463. Написать тест `DateParserTest`: все форматы каталога (66–69, 107–108).
464. Нормализация TZ: сохранять zone из attributes, в UTC для хранения.
465. Написать тест TZ-конверсии.
466. Нормализация пустых полей: обязательные (domain, from, to, departure, price) — элемент отбрасывается с логом.
467. Написать тест: offer с null обязательным → отброшен.
468. Дедуп-ключ `Offer.id` + дополнительный `normalizeKey` (case/space/транслит) — для Combiner.
469. Написать тест дедуп-ключей: «Мінск»==«Минск» (транслит), «Minsk»==«Минск».
470. Создать мапперы источников в `normalize/source/`: `AtlasOfferMapper`, `BzdOfferMapper`, `TicketBusOfferMapper`, `TicketProOfferMapper`, `BelHotelOfferMapper`.
471. `AtlasOfferMapper`: AtlasRide → Offer (поля из каталога сущности: rideId, from/to, price, stops).
472. `AtlasOfferMapper`: partial/errors из AtlasSearchResult → маркер attributes.partial.
473. Написать тест маппера Atlas (полный и частичный rides).
474. `BzdOfferMapper`: BzdTrain → Offer (number+times+класс из BzdCar).
475. Написать тест маппера Bzd (признаки копеек, несколько классов).
476. `TicketBusOfferMapper`: TbRace → Offer (цена из строки, валюты).
477. Написать тест маппера TicketBus.
478. `TicketProOfferMapper`: Event+Offer → EVENT Offer (venue, category).
479. Написать тест маппера TicketPro.
480. `BelHotelOfferMapper`: Hotel+RoomOffer → HOTEL Offer (за ночь vs за всё).
481. Написать тест маппера BelHotel (нормализация цены за ночь).
482. Написать общий `MappersAggregationTest`: 5 мапперов, фикстуры JSON→Offer.
483. Создать фикстуры JSON в `src/test/resources/fixtures/` (по одному на источник, + грязные).
484. Написать тест: грязные фикстуры (Windows-1251, BOM, trailing comma) нормализуются.
485. Integration-тест мапперов: реальный парсер→маппер на live-ответах (если доступно).
486. Создать `OfferValidator`: post-normalize проверки (гео-разброс, date в будущем, seats>0 топологически).
487. Написать тест валидатора: прошедший рейс отброшен (138), sold out отброшен (105).
488. Связать `OfferNormalizer`+`Validator` цепочкой: нормализует→валидирует→отбрасывает.
489. Написать тест цепочки: 100 офферов → на выходе валидные (например 87).
490. Решить передачу: гейтвэи возвращают `List<Offer>` (нормализованные), PrincipalResult errors — сырые сообщения.
491. Написать конвертер `BzdTrain`→Offer в тесте как эталон-бекмарк (время конверсии).
492. [МЕТ] Метрика `offer.normalized{source}` и `offer.dropped{reason}`.
493. [АЛЕРТ] `offer.dropped{reason=parse} > 10/мин` — сигнал смены формата.
494. Проверить согласованность: Price всегда не null (или элемент дропнут).
495. Написать тест: пустой List не рушит комбайн (пустота—успех).
496. ADR-048 (черновик): нормализация выполняется один раз на гейтвее, не повторно.
497. Repository-часть: офферы не храним (ADR-032), но `CachedResult` payload — нормализованный (ADR-042).
498. Написать тест: сериализация Offer→jsonb→Offer (потеря точности BigDecimal).
499. Покрыть каталог контентных ошибок (37–70) юнит-тестами по нормализатору (минимум: каждый Fix из доки).
500. Внести комментарий TODO по курсам валют (live-fetch опционально).
501. Обновить README map (минор).
502. Коммит «ADD: unified-модель, нормализация, мапперы источников».
503. `[✓]` Фаза 6 готова.
504. Написать файл `docs/db-...` не трогаем — модель к БД не относится (отметить).
505. Проверить производительность нормализации (бенчмарк маппера на 1000 rides) — фиксация.
506. Обновить список известных ограничений (валюта-затычка).
507. Зафиксировать: mapping-таблицы транслита разместить в Constants или отдельный enum.
508. Написать тест транслита (основные буквы: с→s, х→h, і→i и т.д.).

## ФАЗА 7. Гейтвеи (509–640)

509. Создать `gateway/SourceGateway.java` (контракт по блюпринту 23) + `SourceKind`.
510. Создать `PrincipalResult<T>` (по блюпринту) в `dto/source/`.
511. Написать тест: PrincipalResult фабрики (ok/failure/partial/skipped/timeout).
512. Создать `TransportGateway`, `EventGateway`, `HotelGateway`.
513. Создать `TransportQuery`, `EventQuery`, `HotelQuery` (unified входные).
514. Написать тест-валидацию query (null/пусто — IllegalArgumentException).
515. Реализовать `AtlasGateway`:
    515a. маппинг TransportQuery → AtlasSearchRequest;
    515b. вызов `AtlasClient.search`;
    515c. быстрый сбой: если источник выключен в `source_state` (`enabled=false`/`draining`) — SKIPPED; ~~circuit~~ — [ВЛ] не делаем (ADR-VL-04);
    515d. обработка HTTP-кодов (500,502,503,429,403,401,404, timeout) → PrincipalResult правильный;
    515e. SSE partial из AtlasSearchResult → `PARTIAL` с rides;
    515f. ошибка без rides → FAILURE;
    515g. маппинг через AtlasOfferMapper;
    515h. `suggest`: AtlasClient.suggestStations → StationSuggestion;
516. Написать юнит-тесты `AtlasGatewayTest` с MockWebServer (каждый HTTP-код + SSE events).
517. Написать интеграционный тест против живого Atlas (tag=live, exclude в CI).
518. Реализовать `BzdGateway`:
    518a. маппинг под BzdClient (station resolve + searchRoute);
    518b. warmup-зависимость: 401 → один warmup-повтор; **403 → без повторов**, `FAILURE` + сообщение пользователю + alert; [ВЛ] ПРОТ-11;
    518c. HTTP-коды и timeout → правильные PrincipalResult;
    518d. `resolveStations` ошибка «станция не найдена» → `CLARIFICATION_REQUIRED`-маркер (по коду, в attributes).
519. Написать юнит-тесты `BzdGatewayTest` с MockWebServer (warmup, 403, пустой результат, таймаут).
520. Реализовать `TicketBusGateway`:
    520a. отображение query → TicketBusSearchRequest;
    520b. работа с сессией (sessionId, renew при «Нарушен доступ») — переиспользовать TicketBusClient;
    520c. race/схевен парсинги через маппер;
    520d. HTTP/текстовые ошибки → PrincipalResult.
521. Написать юнит-тесты `TicketBusGatewayTest` (access-denied renew, пустые рейсы, цены-строки).
522. Реализовать `TicketProGateway`: EventQuery → TicketProClient.get → TicketProEventParser → mapper.
523. Написать юнит-тесты `TicketProGatewayTest` (404 EVENT → пустой EVENT результат, 403 → FAILURE).
524. Реализовать `BelHotelGateway`: HotelQuery → BelHotelClient.search → mapper (за ночь).
525. Написать юнит-тесты `BelHotelGatewayTest` (пусто, sold out, валюты).
526. `StationSuggestion` модель: source, name, id, geo, type.
527. `suggest` общий интерфейс: `SuggestProvider` (поиск городов/станций через все транспортные гейтвеи).
528. Реализовать `SuggestService`: объединяет suggest всех транспортных гейтвеев, топ-5 (АНП-75).
529. Написать тест suggester: «Минск» → 5 корректных уникальных.
530. Внедрить защиту от пустых/дублей в suggest.
531. Вставить в гейтвеи обработку 429/503 Retry-After → retryAfterMs (переиспользовать фазу 5).
532. Таимауты источников: конфиг per-source (source.timeouts.prop) с дефолтами из OkHttpProperties.
533. ADR-049 (черновик): таймауты источников наследуют глобальные + overrides в `source-*.timeout-ms`.
534. Прописать дефолты: connect 5s, read 10s, call 45s, SSE maxDuration 30s.
535. Отключение/включение источника на гейтвее: состояние читается из `SourceStateRepository` (фаза 3).
536. Внедрить check: `if (!state.enabled || state.draining) return SKIPPED`.
537. Написать тест гейтвея: выключенный источник → SKIPPED без сетевого вызова.
538. ~~Интеграция circuit: гейтвей вызывает `CircuitRegistry.get(key).isOpen()`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04: быстрый сбой только по `source_state` (шаг 536).
539. ~~Тест: OPEN circuit → SKIPPED~~ — **[ОТМЕНЕНО]** вместе с 538.
540. Купить гарантию: гейтвей никогда не бросает исключение (кроме восстановленных сценариев запроса).
541. Написать тест-свойство: любой метод гейтвея с любым исключением клиента → PrincipalResult (не throw).
542. Проверить N+1/дубли: suggest и search внутри одного гейтвея не дублируют warmup.
543. Создать `GatewayRegistry` (список гейтвеев, keyed by sourceId).
544. Написать тест registry: ровно 5 гейтвеев, уникальные id.
545. Порядок источников для combiner — по факту завершения ответа (все равны), без приоритетов. [ВЛ] ПРОТ-05
546. Создать `ParallelSourceExecutor`-интерфейс (реализация — фаза 9), гейтвеи остаются «чистыми».
547. Написать тест изоляции: падение bzd не влияет на atlas-результат (уже через PrincipalResult).
548. Прогнать live-интеграции (exclude-теги) локально — фиксируем актуальные ответы.
549. [ПРОВ] Сравнить актуальные JSON-ответы источников с фикстурами — возможно обновление мапперов.
550. Задокументировать расхождения (селектор/поля) в `docs/source-deltas.md`.
551. Проработать `warmup` для Bzd и TicketBus на старте (WarmupScheduler, см. фаза 14) — интерфейс-крюк.
552. Определить `warmup`-интерфейс `Preparable` (prepare()/invalidate()).
553. Реализовать `prepare()` в BzdGateway и TicketBusGateway (обёртка существующих warmup).
554. Написать тест prepare: повторный вызов идемпотентен.
555. [МЕТ] Метрика `source.result{source,status}` — для каждого PrincipalResult.
556. [МЕТ] Метрика `source.latency_ms{source}` (единые имена — ПРОТ-20).
557. Написать тест метрик: состояниеPrincipalResult инкрементирует счётчик.
558. [АЛЕРТ] `source.errors{source}` — N ошибок подряд (окно в конфиге) → **только алерт в лог WARN**, источник отключается вручную флагом. [ВЛ] ADR-VL-04
559. Обновить `docs/19–20` в части числа кодов на уровне клиента (не менять смысл).
560. Коммит «ADD: гейтвеи источников + PrincipalResult + suggest».
561. `[✓]` Фаза 7 готова.

## ФАЗА 8. ~~Circuit breaker (561–660)~~ — [ОТМЕНЕНО] решением владельца

> **[ВЛ] ПРОТ-08/09/В-8: circuit breaker на MVP не делаем.** Вместо него: per-source таймауты, alert по счётчику ошибок (`source.errors`, шаг 558), ручной флаг `source_state.enabled` с `X-Api-Key` (ADR-VL-04). Пункты 561–610 ниже сохраняются как справочный материал для будущей задачи «circuit breaker» (нумерация шагов в нём начинается заново с 561 — это наследие исходного плана): снятые решения владельца — TTL 60 с (макс. 5 мин) и сброс при ручном включении.

561. ~~Создать `circuit/CircuitState` enum, `CircuitKey` (source:endpoint), `CircuitPolicy`~~ — отложено.
562. `CircuitPolicy` свойства: failureThreshold=5, windowMs=30000, openTimeoutMs=60000, maxOpenMs=300000, halfOpenPermits=1.
563. `CircuitBreaker` класс: методы `recordSuccess(), recordFailure()`, `isOpen()`, `state()`.
564. Реализовать скользящее окно: кольцевой буфер отметок времени ошибок (не int-счётчик с reset).
565. Написать юнит-тест окна: 4 ошибки → closed; 5-я → open.
566. Написать тест времени: ошибки старше окна не считаются.
567. Реализовать `CircuitRegistry` (ConcurrentHashMap, get-or-create).
568. Реализовать `isOpen(CircuitKey)`: OPEN → true; HALF_OPEN → true если нет разрешения; CLOSED → false.
569. HALF_OPEN управление: `acquireProbe()` (AtomicBoolean compareAndSet) — 1 пробный.
570. Написать тест: HALF_OPEN пропускает один пробный.
571. Переходы: recordFailure в HALF_OPEN → OPEN (reset timeout); recordSuccess → CLOSED.
572. Написать тест переходов (полный state-machine-тест).
573. Интегрировать с гейтвеями: перед network — `circuit.isOpen(key)` → SKIPPED; после — recordSuccess/Failure.
574. Интегрировать с `ParallelSourceExecutor`: SKIPPED-задача не стартует.
575. Написать интеграционный тест: atlas circuit OPEN → SearchBusesTool вернёт только ticketbus.
576. Персистентность: `CircuitStateRepository` (фаза 3) хранит state; `CircuitSyncScheduler` (фаза 14) пишет/читает.
577. Реализовать восстановление из БД при старте (загрузка всех OPEN).
578. Написать тест восстановления.
579. «Force» правила: `enabled=true` при OPEN из-за флага — НЕ сбрасывает; при переводе enabled=false→true после паузы — сброс OPEN (АНП-102).
580. ADR-050 (черновик): сброс circuit при повторном включении источника (после drain) разрешён.
581. Логика АНП-96: maxOpenMs=5 мин → принудительный HALF_OPEN.
582. Написать тест: circuit переходит HALF_OPEN через maxOpenMs.
583. Логика АНП-98: retry внутри клиента — счётчик ошибок по пользовательскому запросу (1:1).
584. Написать тест: 3 retry внутри = 1 ошибка.
585. Изоляция endpoint'ов (АНП-97): `atlas:stream` отдельно от `atlas:suggest`.
586. Реализовать `CircuitKeyFactory` (source, endpoint).
587. Написать тест: два ключа одного источника не влияют друг на друга.
588. Логика АНП-93: forceSource при OPEN — разрешить 1 пробный (force bypass с пометкой).
589. Написать тест force-source.
590. Логика каскадного открытия (АНП-99): не открываем все — только не отвечающий.
591. Ввести `CircuitStatusView` для дашборда (все состояния).
592. Написать тест view: корректное отображение.
593. Интеграция с health: if все источники OPEN или disabled → ready=false + ALL_SOURCES_UNAVAILABLE.
594. Написать тест health при всех open.
595. Интеграция с кэшем: OPEN → вернуть stale=true из кэша (АНП-95).
596. Написать интеграционный тест: OPEN + кэш → stale результат.
597. [МЕТ] Метрика `source.circuit{source,key}=open`.
598. [МЕТ] Метрика `source.skipped{source}`.
599. [АЛЕРТ] alert при state→OPEN (лог WARN).
600. Написать тест метрик (инкремент при переходе).
601. Секционировать circuit_key: `atlas:stream`, `atlas:suggest`, `bzd:route`, `bzd:stations`, `ticketbus:*`, `ticketpro:event`, `belhotel:hotel`, `llm`.
602. Прописать в `CircuitPolicy` ключи по умолчанию.
603. `GlobalExceptionHandler` не должен видеть raw circuit-исключения (гейтвеи их не бросают).
604. Написать тест: никакой `CircuitOpenException` не уходит в advice.
605. Circuit при LLM-гейте (АНП-100): ключ `llm`, fallback rule-based.
606. Написать тест: 5×429 LLM → OPEN → rguleBased работает.
607. Обновить `docs/22_spec_errors.md` — раздел circuit с фактическими параметрами.
608. Прогнать юнит-тесты и интеграционные на H2.
609. Коммит «ADD: circuit breaker + registry + persistence + state machine».
610. ~~`[✓]` Фаза 8 готова~~ — [ОТМЕНЕНО].

## ФАЗА 9. Параллельный исполнитель и сборка (611–720)

611. Создать `orchestration/ParallelSourceExecutor`.
612. АПИ: `CompletedPair<List<PrincipalResult<T>>> execute(List<SourceCall<T>>, Duration deadline, RequestContext)`.
613. `SourceCall` — функциональный интерфейс `{SourceGateway gateway(); T call();}` (фабрика по гейтвею).
614. Внутренняя модель `CallFuture` (future + key + gateway).
615. Реализовать fan-out: каждый SourceCall на виртуальном потоке (`Thread.startVirtualThread`).
616. Пробросить MDC requestId в дочерние потоки (шаг 56).
617. Реализовать fan-in: `CompletableFuture.allOf` + `join()` — **ждём ВСЕ источники**, без общего обрыва. [ВЛ] ПРОТ-01
618. ~~Deadline-модель: `deadline = min(request.deadlineMs, budget)`~~ — **[ОТМЕНЕНО]** [ВЛ]: общего дедлайна нет; `deadlineMs` — advisory (лог/метрика), таймауты только per-source.
619. ~~По истечении `future.cancel(true)` → TIMEOUT~~ — **[ОТМЕНЕНО]** вместе с 618; TIMEOUT приходит из per-source таймаута клиента.
620. Написать тест: медленный источник (per-source timeout) → TIMEOUT, быстрый — OK; fan-in ждёт обоих.
621. ~~Тест: отменённая задача не оставляет висящих вызовов~~ — **[ОТМЕНЕНО]** вместе с 618.
622. Гарантия порядка: результаты возвращаются в порядке завершения; приоритетов нет (FIFO на входе). [ВЛ] ПРОТ-05
623. Написать тест порядка.
624. Внедрить pre-checks: `enabled`/`draining` → SKIPPED до потока. ~~`circuit`~~ — [ВЛ] не делаем.
625. Написать тест: disabled → SKIPPED, поток не создан (counter assert).
626. Обработка пустого набора задач: вернуть empty быстро.
627. Написать тест пустого вызова.
628. ~~Sleep на `deadline`~~ — **[ОТМЕНЕНО]** вместе с 618; ждём реальные таймауты источников.
629. ~~Тест: не блокируем вызывающий поток больше deadline+slack~~ — переформулировать: fan-in возвращается, когда завершились все источники (или их таймауты).
630. ~~Защита «не отменяем преждевременно» (graceSlack)~~ — **[ОТМЕНЕНО]** вместе с 618.
631. Написать тест slack.
632. Включить `InterruptedException` поведения: при shutdown — отмена, лог.
633. Написать тест shutdown.
634. Создать `ResultCollector`: собирает PrincipalResult'ы в `ToolResult` (offers, warnings, statuses).
635. Реализовать склейку warnings (дедуп текста, limit 5).
636. Написать тест collectors-warnings.
637. Собрать `SourceStatusView` для каждого источника (в ToolResult).
638. Написать тест statuses (OK/FAILURE/TIMEOUT/SKIPPED).
639. `ToolResultSupplier[T]` — общий канал `ToolResult` из результатов (>1 домена).
640. `SearchOrchestrator` — оркестрация полного цикла «промпт → ответ» (собирает фазы 5–12).
641. Создать `PipelineContext` (intent, requestContext, session).
642. Первый контур orchestration: «пустой набор инструментов» — вернуть пустой ответ.
643. Написать тест пустого orchestration.
644. ~~Реализовать выделение инструментов по доменам intent~~ — [ВЛ] **инструменты выбирает LLM** (агентный цикл, ADR-VL-03); фиксированного маппинга по доменам нет, остаётся только проверка аргументов инструмента.
645. Написать тест выбора инструментов.
646. Параллельная логика мультидомена: инструменты тоже параллельны (каждый складывает свои source-calls).
647. Центральный `RunPlan`: список job'ов (tool+query+domains) для fan-out высокого уровня.
648. Написать тест планирования (2 домена → 2 инструмента).
649. ~~Установить total deadline и подбюджеты инструментов~~ — **[ОТМЕНЕНО]** [ВЛ] ПРОТ-01/02.
650. Если источник не ответил вовсе (сеть) — partial-ответ с warning о нём; partial по дедлайну больше не бывает.
651. Написать тест частичного ответа по глобальному deadline.
652. `ResultCollector` агрегирует по доменам и «источники › домены» структуру.
653. Написать тест агрегации.
654. Обработка команд Refine: переиспользует инструменты с фильтрами (см. фаза 12).
655. Связать очередь (фаза 14): обработчик идёт через RequestQueue.
656. Пробросить `RequestContext` всю цепочку (не через статику).
657. Написать тест проброса контекста.
658. Обновить `docs/23_impl_blueprint.md` — раздел параллелизма фактическими таймаутами.
659. Коммит «ADD: параллельный исполнитель, результат-сборка, оркестрация-контур».
660. `[✓]` Фаза 9 готова.

## ФАЗА 10. Инструменты Tools (661–740)

661. Создать `tool/SearchBusesTool` (легас: atlas + ticketbus).
662. `SearchBusesTool.search(intent)` → `ToolResultBus`.
663. Реализовать формирование SourceCalls для TransportQuery.
664. Объединение результатов двух гейтвеев: dedup по id.
665. Написать тест: оба источника ок → offers = A∪B (dedup).
666. Написать тест: один источник FAILURE → другой ок, warning присутствует.
667. Создать `SearchTrainsTool` (bzd): обрабатывает 403/cookie-падения итогово.
668. Написать тест: bzd 403 → пустой результат TRAIN + warning.
669. Создать `SearchEventsTool` (ticketpro).
670. Написать тест: событие удалено (404) → пусто.
671. Создать `SearchHotelsTool` (belhotel).
672. Написать тест: sold out → пусто.
673. Общий `ToolResult` (domain, offers, warnings, statuses, duration).
674. Реализовать `ToolFilter` (личи: из intent: sort/window/budget/directOnly применяется до combiner).
675. Написать тест фильтра (бюджет, directOnly).
676. Применить `roundTrip` поддержку: два вызова инструмента (outbound/inbound).
677. Написать тест roundTrip на уровне плана (2 Sequencе jobs).
678. Multi-day: `dates[]` → N инструментов параллельно (каждый date).
679. Написать тест multi-day (3 даты → 3 группы).
680. Обновить метрики инструментов: `tool.duration{tool}`, `tool.offers{count}`.
681. Написать тест метрик инструмента.
682. [АЛЕРТ] Дублировать alert на «0 результатов у обоих bus-источников» (BUS домен пуст).
683. Написать фикстуры-демо для инструмента.
684. Прогнать unit-тесты; интегрировать с orchestration (пустой контур — уже готов).
685. Коммит «ADD: инструменты поиска (bus/train/events/hotels)».
686. `[✓]` Фаза 10 готова.

## ФАЗА 11. Combiner и Ranker (687–780)

687. Создать `orchestration/Combiner`.
688. Combiner объединяет домены: BUS+TRAIN → единый «транспорт» (для sort по цене/времени).
689. Написать тест: транспортная склейка (поезд+автобус) сортируется по времени.
690. `ComboOffer`: состав (items: domain/offerId/arrival/price), price, duration, tags.
691. Комбо «отель рядом с venue»: HOTEL по гео ≤1 км от EVENT (АНП-170).
692. Написать тест: combiner гео-комбо.
693. Комбо «bus+hotel» (отдых): периметр дней (checkIn=departure).
694. Написать тест: hotel срок покрывает даты события/рейса.
695. roundTrip склейка: outbound+inbound в ComboOffer.
696. Написать тест roundTrip-комбо.
697. multi-day: комбо по группам дат (distinct).
698. Фильтр бюджета (HP-16): отбрасывает дороже budget.
699. Написать тест бюджета.
700. Фильтр sold out/0 мест (105).
701. Тест «нет мест → не участвует».
702. Дедупликация: ключ `offerId` (и normalizeKey).
703. Тест дедупа (дубли BZD и TicketBus → один).
704. Конверсия валют для сравнения: по amountByn.
705. Тест: цена 25 RUB конвертируется в BYN корректно (затычка+флаг).
706. `Ranker`: сортировка CHEAPEST/FASTEST/BEST.
707. CHEAPEST: по amountByn asc (фикс).
708. FASTEST: по (arrival-departure) asc.
709. BEST: score = 0.5*относит.цена + 0.3*длительность + 0.2*вес прямых/рейтинг.
710. Написать тесты каждого rank-режима.
711. top-N (`maxOffers`) после сортировки.
712. Тест top-N.
713. Стабильная сортировка (tie-break: source priority).
714. Тест tie-break.
715. `Combiner` результат → `SearchResponse.groups` (по доменам) + `combos`.
716. Написать тест: структура response (группы/комбо) по фикстуре.
717. Проверка пустоты: если 0 офферов во всем — ответ с warning ALL_SOURCES_UNAVAILABLE (если все источники).
718. Тест: 0 офферов → корректная семантика (но warn иначе).
719. ADR-051 (черновик): rank-модель простая (v1), score формула фиксируется здесь.
720. Обновить `docs/03_happy_combos_refine.md` ссылки на Combiner реализации.
721. Коммит «ADD: Combiner + Ranker + ComboOffer».
722. `[✓]` Фаза 11 готова.

## ФАЗА 12. LLM-гейт и агент (723–820)

723. Создать `llm/LlmGateway` интерфейс: `LlmResponse complete(LlmRequest)`; **agentic-режим**: `List<ToolCall> nextTools(...)` + `complete(...)`. [ВЛ] ADR-VL-03
724. `LlmRequest`: systemPrompt, **история диалога (≤20 сообщений)**, список инструментов, timeoutMs. ~~schemaHint (JSON)~~ — [ВЛ] упрощено.
725. `LlmResponse`: text, toolCalls, usage, ms, raw; надёж: failure-маркер.
726. `LlmProperties`: **OpenRouter** (`baseUrl=https://openrouter.ai/api/v1`), `apiKey(ref env OPENROUTER_API_KEY)`, `model` (env `OPENROUTER_MODEL`), `timeoutMs=30000`, `contextWindow=20`, `maxToolRounds=2`. [ВЛ]
727. Реализация-заглушка `LlmGatewayStub` (детерминированно возвращает фикстуры) — для тестов/дев.
728. Создать `agent/Extractor`: промпт→(SearchIntent JSON).
729. Построить JSON-схему `SearchIntent` (domains, from/to, dates, pax, roundTrip, prefs, sources).
730. `Extractor` поддерживает schema-hint (JSON-фикстура).
731. Написать тесты Extractor: 10 примеров промптов (из happy-доков) → правильные intent.
732. `Validator` — проверка intent (обязательные поля: хотя бы домен и direction).
733. Тест Validator: пустой from в BUS → error CLARIFICATION_REQUIRED.
734. `Extractor` с fallback: если LLM недоступен — `RuleBasedFallback`.
735. `RuleBasedFallback` — regex-парсер (домен, города, даты) из каталога токенов.
736. Написать тесты fallback: простые и средние промпты.
737. ~~Интеграция circuit key `llm`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
738. Тест: LLM недоступен/таймаут → fallback (текст из офферов), пользователь не ждёт 500.
739. `Clarifier`: построение вопроса пользователю по недостающим полям.
740. Clarifier-сценарии: город не найден (АНП-74), станции-уточнение (АНП-94), даты, где именно (АНП-168).
741. Написать тесты Clarifier (5 сообщений из каталога сообщений).
742. `Refiner`: **отдельного refine-API нет** — уточнения приходят обычными репликами в чат, LLM сам решает, какие инструменты вызвать. [ВЛ] ADR-VL-02/019
743. ~~Реализовать merge-логику refine без дублей~~ — [ВЛ] не делаем (контекст = история диалога).
744. ~~Тест Refiner~~ — [ВЛ] не делаем.
745. `Summarizer`: задание LLM — резюме по offers+intent (без галлюцинаций, топ-факты).
746. Реализовать prompt-шаблоны в `resources/prompts/*.md`.
747. Тест Summarizer: на фикстуре offers → краткое резюме (snapshot-тест).
748. Guard пост-валидации (из доков): проверка резюме на факты (даты/цены из offers).
749. Тест Guard: резюме утверждает цену, которой нет — фолбэк «повторное резюме» или правка.
750. ~~Ввод значения в `SearchResponse.summary`~~ — [ВЛ] ответ агента — **текст** (`text/plain`), без DTO.
751. ~~Связать LLM-гейт и circuit breaker~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
752. Написать тест: LLM timeout → LLM_UNAVAILABLE сообщение, не 500.
753. ADR-052 (черновик): при недоступном LLM — rule-based, функциональность урезанная, сервис жив.
754. Покрыть сценарий смерти LLM посреди запроса (extract ok, summary fail) — summary повторно через fallback.
755. ~~Union intent «forceSource»~~ — [ВЛ] не делаем (источник выбирает LLM).
756. ~~Тест forceSource~~ — [ВЛ] не делаем.
757. `LlmGateway` интерфейс — смена провайдера без изменения кода (ADR).
758. Документировать API LLM (контракт JSON) в `docs/llm-contract.md`.
759. Написать integration-тест со стубом/моком для полного цикла агент.
760. Залогировать промпты/резюме (без секретов) в `request_log.intentJson` / summary-файл.
761. Тест: промпты не содержат API-ключей.
762. Коммит «ADD: LLM-гейт (OpenRouter, function calling), агентный цикл, кларификация».
762.1. [ВЛ] Реализовать **агентный цикл**: LLM возвращает `tool_calls` → `ParallelSourceExecutor` выполняет их параллельно и **ждёт все** → результаты (`{status, offers|error}`) возвращаются в LLM → повтор до `maxToolRounds` → финальный текст (ADR-VL-03).
762.2. [ВЛ] Инструменты-LLM: по одному на источник (`search_atlasbus`, `search_ticketbus`, `search_bzd`, `search_ticketpro`, `search_belhotel`) + `get_offers`; лимит 1 вызов на источник за раунд; лимит раундов — из настроек агента (`AgentProperties.llm.maxToolRounds=2`).
762.3. [ВЛ] Fallback-решатель (пока нет ключа OpenRouter): тот же цикл, выбор источников по контексту/флагам, финальный текст собирается из офферов по шаблону.
762.4. [ВЛ] Тест агентного цикла (на моке LLM): 2 раунда, tool-результаты с ошибкой источника, упавший источник упомянут в тексте.
763. `[✓]` Фаза 12 готова.

## ФАЗА 13. Оркестрация и Web API (764–850)

764. `SearchOrchestrator` — полный контур: request → intent → validate/Clarify → tools(fan-out) → combine → rank → summarize → guard → response.
765. Написать сквозной тест happy (фикстура → интегрировано) без LLM (fallback).
766. `RefineOrchestrator` — refine-контур (базовый session + новый intent merge + новый ответ).
767. Написать тест refine (замена интента, тот же sessionId).
768. `web/AgentController`: **`POST /api/v1/agent/chat`** (`sessionId?`, `text`) → **`text/plain`** со строкой ответа. ~~`/agent/search`, `/agent/refine`~~ — [ВЛ] У-1/В-7: один чат-эндпоинт, отдельного refine нет.
769. Маппер DTO: SearchRequest(web) → PipelineContext.
770. Валидация через `@Valid` (jakarta).
771. Возврат: 200 + `text/plain` (строка ответа агента) либо текст ошибки через advice. ~~SearchResponse/ErrorResponse~~ — [ВЛ] У-1.
772. Написать тест контроллера (MockMvc): happy → 200 `text/plain` с текстом.
773. `SourcesAdminController`: GET /sources, POST /sources/{name}/enable|disable — **за `X-Api-Key`** (ADR-VL-10).
774. Реализовать включение с ramp-up: после enable — первый период 10% трафика (АНП-103).
775. Реализовать `RampUpGate` (window 30 сек).
776. Написать тест ramp-up.
777. drain при disable (АНП-106): новые запросы стоп, активные завершаются.
778. Написать тест drain (активный SSE вызов получает завершение).
779. `HealthController`: live/ready.
780. Readiness включает: БД, queue.reject=false, не «все источники отключены».
781. Написать тесты health-состояний.
782. Связать idempotency (фаза 3): дублирующий requestId → возврат прежнего.
783. Написать тест идемпотентности на уровне контроллера.
784. Блок-обработку больших промптов: length limit 2000 (Validator).
785. Языковой параметр lang → резолвер сообщений (ru/en).
786. Тест: lang=en → сообщения на английском.
787. Timeout-механизм HTTP: дефолт server-таймауты, deadline в контексте.
788. Написать тест: долгого источника → ответ в границах deadline.
789. Метаданные ответа: `sourceStatuses` в каждом group.
790. Написать тест statuses.
791. `Warnings` собираются по всем источникам (дедуп).
792. Тест предупреждений.
793. Управление ошибкой `ALL_SOURCES_UNAVAILABLE` — сообщение через i18n (фаза 16).
794. Ответ при `CLARIFICATION_REQUIRED` — `requiresClarification=true`, message.
795. Тест клариф.
796. CRUD сессий через репозиторий (чтение/создание при поиске).
797. Тест: поиск создаёт session.
798. Тест: повторный поиск без session → новый sessionId.
799. Прогон e2e Smoke (MockMvc): «автобус Минск-Брест» → ответ 200 с offers.
800. Прогон всех unit-тестов.
801. Коммит «ADD: оркестрация + web API + admin».
802. `[✓]` Фаза 13 готова.

## ФАЗА 14. Очередь, сессии, кэш, планировщики на БД (803–900)

803. `queue/RequestQueue` — bounded `ArrayBlockingQueue<RequestTask>` (initialCapacity=100).
804. `RequestTask`: requestId, priority, context, Callable<Void>.
805. `QueueProperties`: capacity, consumers, priority aging, reject.
806. Приоритеты: default HIGH; фоновые (кэш-refresh) LOW.
807. Реализовать `RequestQueue` put-timeout (лимит) с политикой reject.
808. `put` при полной очереди → `queue.reject=true` на период.
809. `take` из consumer-потоков (4–8) → `SearchOrchestrator`.
810. Написать тест: очередь полна → QUEUE_REJECTED (503).
811. ~~Aging: увеличивать приоритет старых задач~~ — **[ОТМЕНЕНО]** [ВЛ] ПРОТ-05: FIFO без приоритетов и старения.
812. ~~Тест aging~~ — **[ОТМЕНЕНО]** вместе с 811.
813. Дедупликация в очереди по requestId (Map-монитор) — оставляем (защита от двойного клика, порядок FIFO не меняет).
814. Тест: два идентичных requestId в очереди — второй игнорируется.
815. Backpressure: как `queue.reject` отключает приём (модель флага).
816. [МЕТ] Метрики `queue.length`, `queue.max`, `queue.rejected`.
817. Housekeeper-интеграция: consumer-пулы не конфликтуют с housekeeper.
818. `session/SessionStore` поверх `SessionRepository`:
   - get/put/update/touch/delete.
819. `Session` domain: id, **messages (список реплик)**, createdAt, lastAccessAt, **ttl=15 мин**; `state ∈ {NEW, ACTIVE, EXPIRED}` (без `CANCELLED`). [ВЛ] ADR-VL-02
820. `SessionRepository`-реализация (фаза 3) → `SessionStore`.
821. Реализовать `touch()` — продлевает TTL (15 мин с последнего обращения); `expire()` при обращении, если просрочен.
822. Тест: истекшая сессия (старше 15 мин) → SESSION_EXPIRED.
823. `SessionHousekeeper` (scheduler): каждую минуту находит expired → delete batch (1000) (каскадом удаляет `session_message`).
824. Тест housekeeper-чистки.
825. Refine-блокировка `SELECT ... FOR UPDATE` — [ВЛ] не делаем (отмены/параллельного refine нет).
826. ~~Тест параллельного refine~~ — [ВЛ] не делаем.
826.1. [ВЛ] `SessionMessageRepository`: `append` (user/agent), `findLast(sessionId, limit=20)` для контекста LLM, `findAll` для отладки. Тест: порядок и лимит 20.
827. `cache/CacheManager` поверх `CacheRepository`:
   - get/put/putIfAbsent/delete, TTL, stale.
828. Ключи кэша: `sourceId:endpoint:normParams`.
829. Реализовать `CacheAccessor` helper (get-or-load-with-lock? без lock — race ок для кэша).
830. Тест: race двух потоков — без дублей.
831. `CacheRefreshScheduler`: раз в 5 мин обновляет suggest-справочники (города) в кэше.
832. Тест update справочников.
833. Кэширование результатов: TTL 5 мин; stale-возврат — только вместе с предупреждением пользователю. ~~при OPEN~~ — [ВЛ] OPEN не бывает (ADR-VL-04).
834. Тест stale.
835. `WarmupScheduler`: при старте + повтор 5 мин — `prepare()` для Bzd/TicketBus.
836. При ошибке warmup — повтор через 30 сек (не падает приложение).
837. Тест: warmup retry.
838. ~~`CircuitScheduler`: переходы OPEN→HALF_OPEN~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
839. ~~Тест синхронизации circuit~~ — **[ОТМЕНЕНО]** вместе с 838.
840. `QueueMonitor`-scheduler: метрики очереди, флаг reject.
841. Настройка таймингов в `application.properties` (React cron ex `0/5 * * ? * *`).
842. Пул планировщика: 2 потока (именованные), изоляция джобов separate `@Scheduled` группы.
843. Тест: джобы работают параллельно, без блокировок.
844. Обновить README/доки в части «как работает очередь/сессии/кэш».
845. Интеграционный тест полного цикла: очередь → оркестрация → сессия → кэш.
846. Проверить graceful shutdown: очередь drain-timed (активные дорабатывают 30 сек).
847. Тест shutdown.
848. [МЕТ] Метрики `session.active`, `cache.hit`, `cache.miss`, `cache.stale`.
849. [АЛЕРТ] alert при `queue.length>90%` — WARN.
850. Коммит «ADD: очередь, сессии, кэш, планировщики на БД».
851. `[✓]` Фаза 14 готова.

## ФАЗА 15. Метрики, алерты, логи, health (852–930)

852. `MetricsCollector` — счётчики: инкремент threadsafe, tag-и.
853. Виды: Counter, Histogram (простой), Gauge.
854. Реализовать `CounterRegistry` (ConcurrentHashMap<String, LongAdder>).
855. `HistogramRegistry` (bucket'ы: 0-100ms,100-500,500-1s,>1s).
856. Тесты регистров.
857. Собрать метрики: http, db (фаза 3–4), source (фаза 7), tool (10), llm (12), queue (14). ~~circuit (8)~~ — [ВЛ] не делаем. Имена — по единому словарю `25_contradictions.md` (ПРОТ-20).
858. ~~Экспорт /actuator/prometheus~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-06: счётчики в памяти + лог, без actuator/prometheus.
859. ~~Тест prometheus-выражений~~ — **[ОТМЕНЕНО]** вместе с 858.
860. `AlertEvaluator` — локальные пороги, вызываемые из метрик-записей.
861. Пороги: parse.errors>10/мин, source.errors>N/мин (порог в конфиге), queue.length>90%, db errors>5/min, INTERNAL_ERROR>0.
862. Режимы реакции: log WARN/ERROR + вебхук-заглушка (интерфейс `AlertSink`).
863. Реализовать два AlertSink: `LogAlertSink`, `NoopAlertSink`.
864. Написать тесты срабатываний.
865. Домен-специфичные: alert «смена формата» (parse.CSS-селектор упал), «устарел Constants» (404-домен), «сертификат» (SSL), «анти-бот» (капча).
866. Alert «все источники недоступны» → ERROR.
867. `/actuator/health` объединить с кастомными readiness (БД, очередь, источники).
868. Логирование: MDC requestId/source/code/status; `storm-app.log`, `storm-db.log`, `storm-source.log`.
869. `logback-spring.xml` с профилями dev/prod.
870. Проверка «нет секретов в логах» — фильтр/маска (apiKey).
871. Тест маскирования логов.
872. ~~/actuator/metrics и /prometheus — роли admin~~ — [ВЛ] эндпоинтов нет; диагностика/источники закрываем `X-Api-Key` (ADR-VL-10).
873. Тест прав доступа.
874. Request-id во всех лог-файлах (проверка).
875. Алерты фиксированны в `docs/alerts.md` таблица.
876. Метрики в `docs/metrics.md`.
877. Сквозной алерт-тест (эмуляция parse.error) → лог.
878. Совместимость с H2 (метрики не зависят от СУБД).
879. Сквозной тест: 1 поиск → есть метрики всех компонентов.
880. Прогнать все юнит.
881. Коммит «ADD: метрики, алерты, логи, health».
882. `[✓]` Фаза 15 готова.

## ФАЗА 16. Каталог сообщений и покрытие каталога ошибок 1–235 (883–980)

883. Создать полный `messages.properties` (по фаза 5 заготовке) — список.
884. Каждое сообщение: `code.message` и `code.description`.
885. Связать с ErrorCode: `MessageResolver` (phase 5).
886. Добавить `messages_en.properties` (переводы).
887. Тест: для каждого code есть ru+en.
888. Катализация unhappy-каталога: таблица «ошибка #, причина, класс, ErrorCode, сообщение, тест» — в `docs/error-matrix.md`.
889. Покрыть каждую ошибку 1–235 юнит-интеграционным тестом (по мере фаз) — отметить done/долг.
890. Завести script `check_error_coverage.sh` (парсит error-matrix, сверяет done).
891. Прогнать покрытие — список «красных» задач.
892. Обработать «красные» (дописать тесты/фикстуры).
893. Успешно: 100% каталога имеют «class+test».
894. Обновить README (карта).
895. Коммит «UPDATE: каталог сообщений, покрытие unhappy-каталога».
896. `[✓]` Фаза 16 готова.

## ФАЗА 17. E2E, нагрузка, приёмка (897–980)

897. Создать сквозные сценарии в `src/test/java/.../e2e/` (по happy-докам 01–03).
898. E2E-тест HP-1 (автобус, happy) через полный контур.
899. E2E HP-2 (поезд, окно времени).
900. E2E HP-5 (сравнение транспорта).
901. E2E HP-11 (roundTrip).
902. E2E HP-16 (бюджет).
903. E2E комбо 3 доменов (happy 29+).
904. E2E refine (happy 12).
905. E2E unhappy: источник 500 → warning, сервис жив.
906. E2E unhappy: все источники OPEN → ALL_SOURCES_UNAVAILABLE.
907. E2E unhappy: LLM недоступен → fallback.
908. E2E unhappy: очередь переполнена → 503.
909. E2E unhappy: сессия протухла → SESSION_EXPIRED.
910. Прогнать в CI (GitHub Actions) — матрица: JDK 21, H2 тесты, pg сервис.
911. Настроить CI шаги: compile, test, flyway-migrate, e2e.
912. CI не падает на live-интеграциях (теги exclude).
913. Load-бенчмарк: `docs/load-plan.md`, 50 RPS сценарий → латентность, метрики.
914. Замерить baseline latency (no llama) до оптимизаций.
915. Настроить профиль приёмочный: `prod` с pg, конфиги прода.
916. Тест проде: стартует, migrate, health.
917. дым: запуск в Docker (Dockerfile multi-stage), миграции в compose.
918. Dockerfile: stage build (mvn), runtime (temurin-jre).
919. Compose: app+pg+healthcheck, порты.
920. Тест compose (локальный smoke).
921. Проверить идемпотентность ретраев джобов (housekeeper повторный запуск безопасен).
922. Проверить восстановление после рестарта: session/кэш/состояние восстанавливаются из БД.
923. Smoke: старые живые источники отвечают (Atlas, BZD, TicketBus, TicketPro, BelHotel).
924. Прогнать весь тест-suite несколько раз (стабильность).
925. Зафиксировать smoke-результаты в `docs/e2e-report.md`.
926. Аудит секретов перед деплоем.
927. Проверить лицензии (окно).
928. Определить версию релиза + тег.
929. Коммит «E2E: сквозные тесты, CI, docker».
930. `[✓]` Фаза 17 готова.

## ФАЗА 18. Эксплуатация и полировка (931–1000)

931. Документировать runbook (старт, остановка, диагностика).
932. Как смотреть логи/метрики (docs/ops.md).
933. Как перевключить источник вручную (db update source_state).
934. ~~Как сбросить circuit вручную~~ — [ВЛ] circuit нет; вместо этого — как включить/выключить источник флагом (админ, `X-Api-Key`).
935. Как почистить очередь.
936. Как восстановить после crashed-housekeeper (идемпотентность).
937. Как обновить Константы (домены/UA/токены) — процедура.
938. Как обновить курсы валют.
939. Как добавить новый источник (шаблон gateway+parser+маппер+конфиг).
940. Как добавить новый ErrorCode.
941. Как добавить сообщение i18n.
942. Как добавить метрику/алерт.
943. Деплой-инструкции (Docker, migrate, monitor).
944. Ротация логов (logback rolling).
945. Бэкапы БД (pg_dump скрипт) + документ.
946. Ретеншн-чистки schedule (production).
947. Обновить ADR по мере эксплуатации.
948. Прогнать полный тест-suite в CI (финальный green).
949. Смоук-тест прода после деплоя.
950. Составить список известных ограничений (currency stub, no redis, single-node).
951. Рекомендации по будущему: Redis для кэша, Testcontainers, метрики в Прометей, multi-node.
952. Приемка: чек-лист по требованиям МВП (домены, unhappy-пути, каталог).
953. Итоговый коммит release.
954. `[✓]` Проект готов к МВП.

---

## Приложение A. Сводный чек-лист (порядок проверки перед релизом)

- [ ] АДР 1–30 существуют и обоснованы (по `25_contradictions.md`, раздел 40.1)
- [ ] БД: миграции V1–V16 применяются, индексы есть, hot-queries оптимизированы (EXPLAIN)
- [ ] Исключения: каталог 1–235 маппится в ErrorCode → HTTP → i18n
- [ ] Изоляция: ни один источник не роняет сессию (гарантированный PrincipalResult)
- [ ] ~~Circuit: threshold/окна, АНП-89…100~~ — [ВЛ] circuit не делаем; вместо этого: `source.errors` + alert + ручной флаг `enabled`
- [ ] Параллельность: await-all без дедлайна, FIFO-порядок, MDC (отмены нет — [ВЛ] У-7)
- [ ] Инструменты и combiner/ranker работают (happy 1–80)
- [ ] LLM fallback: rule-based работает при недоступности LLM
- [ ] Метрики/алерты на каждой ошибке, логи с request-id
- [ ] CI зелёный, e2e сценарии выполнены, нагрузка измерена

## Приложение B. Карта «каталог ошибок 1–235 → фаза/класс/тест» (шаблон)

| # ошибки | Источник | Фаза | Класс | ErrorCode | Тест |
|---|---|---|---|---|---|
| 1–15 | Общие HTTP | 5,7 | ClientException / гейтвеи | SOURCE_UNAVAILABLE и др. | At<source>GatewayTest |
| 16–36 | Сетевые | 5,7 | ClientException(timeout) | SOURCE_TIMEOUT | ... |
| 37–70 | Контент | 6 | OfferNormalizer/ParseException | SOURCE_PARSE_ERROR | MappingTests |
| 71–86 | Atlas | 6,7 | AtlasSseParser→Gateway | — | AtlasGatewayTest |
| 87–120 | BZD | 6,7 | BzdGateway+warmup | SOURCE_AUTHORIZATION | BzdGatewayTest |
| 121–150 | TicketBus | 6,7 | TicketBusGateway | — | TicketBusGatewayTest |
| 151–170 | TicketPro | 6,7 | TicketProGateway | — | Test |
| 171–200 | BelHotel | 6,7 | BelHotelGateway | — | Test |
| 201–220 | Юридические | 13,17 | SourcesAdmin | — | E2E |
| 221–235 | РБ/РФ | 6,13 | Local/Normalizer | — | E2E |

## Приложение C. Детальный план тестирования (шаги 956–1030)

Тесты группируются по слоям; каждый пункт = автотест с однозначным условием прохождения.

956. Юнит `ErrorCodeTest`: 16 кодов → HTTP-статус + ключ i18n; покрытие 100%.
957. Юнит `MessageResolverTest`: ru/en, fallback на ru, отсутствие ключа → сам код.
958. Контракт `ErrorResponseTest`: сериализация в JSON, поле requestId обязательное.
959. Advice-тест `GlobalExceptionHandlerTest`: все коды → корректные HTTP и тело.
960. Юнит `RequestIdFilterTest`: генерация, проброс в MDC, парсинг заголовка.
961. Интеграционный `RequestIdFlowTest`: контроллер → advice → лог содержит один и тот же requestId.
962. ~~Юнит `CircuitBreakerWindowTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
963. ~~Юнит `CircuitTransitionTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
964. Юнит `SourceErrorCountTest`: 3 внутренних retry = **1** ошибка в `source.errors` (АНП-98, ПРОТ-10). Оставляем — правило счёта ошибок нужно даже без breaker.
965. ~~Юнит `CircuitEndpointIsolationTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
966. ~~Юнит `CircuitForceProbeTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
967. ~~Юнит `CircuitMaxOpenTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
968. ~~Интеграционный `CircuitCacheStaleTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
969. ~~Интеграционный `CircuitPersistenceTest`~~ — **[ОТМЕНЕНО]** [ВЛ] ADR-VL-04.
970. Юнит `ParallelExecutorFanOutTest`: N задач → N виртуальных потоков.
971. ~~Юнит `ParallelFactorDeadlineTest`: TIMEOUT к deadline~~ — [ВЛ] переименовать: TIMEOUT из per-source таймаута, fan-in ждёт все.
973. ~~Юнит `ParallelFactorOrderTest`: порядок по priority~~ — [ВЛ] порядок по завершению, приоритетов нет.
974. Юнит `ParallelFactorSkipTest`: disabled → SKIPPED, поток не создаётся. ~~circuit~~ — [ВЛ] не делаем.
975. Интеграционный `ParallelFactorMDCTest`: requestId виден в дочернем потоке.
976. Юнит `AtlasGatewayHttpTest`: MockWebServer для 500/502/503/429/403/401/404/504 и timeouts.
977. Юнит `AtlasGatewaySseTest`: события progress/rides/done/error/unknown/broken-json/duplicate.
978. Юнит `AtlasGatewayPartialTest`: partial SSE → PARTIAL с собранными rides.
979. Юнит `AtlasGatewayEmptyTest`: done без rides → легитимный пустой результат.
980. Интеграционный `AtlasLiveTest` (tag=live): реальный поиск Минск→Брест.
981. Юнит `BzdGatewayWarmupTest`: warmup при первом вызове, повторный — без повторной загрузки.
982. Юнит `BzdGateway403Test`: 403 → retry warmup → снова 403 → FAILURE.
983. Юнит `BzdGatewayStationResolveTest`: станция не найдена → CLARIFICATION_REQUIRED.
984. Юнит `TicketBusSessionRenewTest`: "Нарушен доступ" → dropSession + повторный вызов.
985. Юнит `TicketBusStationResolveTest`: пустой ответ suggest → PR, без сбоев.
986. Юнит `TicketPro404Test`: событие удалено → пустой результат EVENT.
987. Юнит `BelHotelEncodingTest`: windows-1251 чанки парсятся без кракозябр.
988. Юнит `OfferNormalizerPriceBzdTest`: 2500 копеек → 25.00 BYN.
989. Юнит `OfferNormalizerRangeTest`: "от 25 BYN" → 25.
990. Юнит `OfferNormalizerCurrencyTest`: RUB/USD конвертируется в BYN (с флагом).
991. Юнит `OfferNormalizerNullTest`: null обязательное поле → элемент отброшен.
992. Юнит `DateParserLocaleTest`: "15 июня 2025", "завтра", "08:20 PM", MSK.
993. Юнит `TranslitKeyTest`: "Мінск"==="Минск"=== "Minsk" ключ.
994. Юнит `MapperAtlasTest`: AtlasRide→Offer эталон.
995. Юнит `MapperBzdTest`: BzdTrain→Offer, несколько классов.
996. Юнит `MapperTicketBusTest`: TbRace→Offer.
997. Юнит `MapperTicketProTest`: Event+Offer→Offer.
998. Юнит `MapperBelHotelTest`: Hotel+RoomOffer→Offer, цена за ночь.
999. Интеграционный `NormalizerPipelineTest`: грязные фикстуры → валидные офферы.
1000. Юнит `CombinerDedupTest`: дубликаты BZD/TicketBus → 1 оффер.
1001. Юнит `CombinerGeoTargetTest`: отель ≤1 км от venue → combo.
1002. Юнит `CombinerBudgetTest`: оффер дороже бюджета отброшен.
1003. Юнит `CombinerRoundTripTest`: outbound+inbound → комбо.
1004. Юнит `RankerCheapestTest`: сортировка по amountByn.
1005. Юнит `RankerFastestTest`: сортировка по длительности.
1006. Юнит `RankerBestTest`: скоры и tie-break по priority.
1007. Юнит `RankerTopNTest`: maxOffers ограничивает вывод.
1008. Юнит `ToolBusTest`: оба источника ок → объединение без дублей.
1009. Юнит `ToolBusPartialTest`: один источник FAILURE → offers от второго + warning.
1010. Юнит `ToolBusFilterTest`: бюджет/directOnly применяются до ответа.
1011. Юнит `ToolTrain403Test`: BZD 403 → пустой TRAIN + warning.
1012. Юнит `ToolEventsTest`: 404 → пусто, sold out → пусто.
1013. Юнит `ToolHotelsTest`: без цен → пусто/частично.
1014. Юнит `ExtractorHappyTest`: 10 промптов happy-доков → корректные SearchIntent.
1015. Юнит `ValidatorTest`: недостающие поля → CLARIFICATION_REQUIRED.
1016. Юнит `RuleBasedFallbackTest`: 5 промптов без LLM.
1017. Юнит `ClarifierTest`: 5 сценариев → корректные вопросы.
1018. Юнит `RefinerTest`: merge-интента, противоречия, forceSource.
1019. Юнит `SummarizerTest`: резюме на фикстуре offers (snapshot).
1020. Юнит `GuardPostTest`: резюме с несуществующей ценой → повторный вызов.
1021. Интеграционный `AgentControllerHappyTest`: MockMvc полный контур (fallback LLM).
1022. Интеграционный `AgentControllerValidationTest`: невалидное тело → 400.
1023. Интеграционный `AgentControllerIdempotentTest`: повторный requestId → прежний ответ.
1024. Интеграционный `SourcesAdminTest`: disable → drain, enable → ramp-up.
1025. Интеграционный `QueueRejectTest`: переполнение → 503 QUEUE_REJECTED.
1026. Интеграционный `SessionExpiredTest`: истёкшая сессия → SESSION_EXPIRED.
1027. Сквозной `E2eHappyBusTest`: «Автобус Минск-Брест» (фикстуры) → 200, текстовый ответ.
1028. Сквозной `E2eUnhappySourceTest`: источник 500 → текст с упоминанием упавшего источника, сервис жив (без breaker).
1029. Сквозной `E2eAllDownTest`: все источники FAILURE → текст «источники недоступны». ~~OPEN~~ — [ВЛ] circuit нет.
1030. Сквозной `E2eQueueTest`: Мейн-путь с очередью → текстовый ответ.

---

## Приложение D. Порядок слияния фаз (этапный план)

1031. Фазы 0–2 (каркас+БД) — «инфраструктурный» merge.
1032. Фазы 3–4 (схема+оптимизация) — «schema» merge.
1033. Фазы 5–6 (исключения+модель) — «core» merge.
1034. Фаза 7 (гейтвеи) — «sources» merge. ~~Фазы 7–8 (гейтвеи+circuit)~~ — [ВЛ] circuit отменён.
1035. Фазы 9–10 (параллельность+tools) — «orchestration» merge.
1036. Фазы 11–12 (combiner+LLM) — «agent» merge.
1037. Фазы 13–14 (web+очередь) — «api» merge.
1038. Фазы 15–16 (метрики+каталог ошибок) — «observability» merge.
1039. Фазы 17–18 (E2E+эксплуатация) — «release» merge.
1040. Каждый merge: ADR записан, тесты зелёные, каталог ошибок покрыт.

Конец плана. Расчёт: **~1030 шагов**, из них часть помечена `[ОТМЕНЕНО]` по решениям владельца (`25_contradictions.md`); при необходимости любая фаза расширяется под-шагами уровня «метод → тест».