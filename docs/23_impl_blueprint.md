# Блюпринт реализации (нижний уровень): API, гейтвеи, планировщики, обработчики, исключения, последовательный вызов источников

Назначение документа — быть **исходником для построения детального плана (1000+ шагов)**.

> **Обновлено по решениям владельца** ([`25_contradictions.md`](25_contradictions.md), разделы 0, 0.1, 0.2). `~~отменено~~` — правила, **не реализуемые в MVP**; `[ВЛ]` — действующие решения.
> Ключевые отмены: circuit breaker (ADR-VL-04), общий `deadlineMs`/отмена запросов (ADR-VL-09, ПРОТ-01), JSON-контракт `SearchResponse` и `openapi.yml` (ADR-VL-01), эндпоинты `search`/`refine` (ADR-VL-01), очередь целиком — `RequestQueue`/воркеры/backpressure (ADR-VL-05 → **заменено ADR-VL-15**), приоритеты и aging очереди (ADR-VL-05/ADR-VL-12), параллельный fan-out на виртуальных потоках (ADR-VL-09 → **заменено ADR-VL-15**), несколько `OkHttpClient` (ADR-008 → **заменено ADR-VL-15**), Prometheus (ADR-VL-06), Redis-сессии → Postgres `session_message` с TTL 15 мин (ADR-VL-02), `forceSource` (ADR-VL-03), обход anti-bot (ADR-VL-12).
Здесь зафиксированы контракты нижнего уровня: какие пакеты/классы/интерфейсы строить, какие REST-эндпоинты и JSON-структуры, как устроены гейтвеи, планировщики, обработчики, система исключений и перехват, сообщения ошибок, порядок вызова источников и целостность данных.

## 1. Рамки и допущения

### Что уже есть в коде (не переделывать, а оборачивать)

| Слой | Что есть |
|---|---|
| Клиенты | `AtlasClient`, `BzdClient`, `TicketBusClient`, `TicketProClient`, `BelHotelClient`, `BzdCookieJar` |
| Сервисы | `AtlasService`, `TicketBusService`, `TicketProService` |
| Парсеры | `AtlasSseParser`, `AtlasStationParser`, `BzdStationParser`, `BzdRouteParser`, `TicketBus*Parser`, `TicketProEventParser`, `BelHotelResponseParser` |
| Entity | `entity.atlas.*`, `entity.bzd.*`, `entity.tb.*`, `entity.ticket.*`, `entity.hotel.*` |
| DTO запросов | `AtlasSearchRequest`, `TicketBusSearchRequest`, `HotelSearchRequest` |
| Исключения клиентов/сервисов | `AtlasClientException`, `BzdClientException`, `TicketBusServiceException`, … (все наследуют `RuntimeException`, помечены `@ResponseStatus(BAD_GATEWAY)`) |
| Конфиг | `OkHttpProperties`, `AppConfig` (один общий `OkHttpClient`), `Constants`, `Local` |

### Чего нет — строим

LLM-агент (**агентный цикл с OpenRouter**: выбор инструментов LLM → **последовательный** вызов источников → ответ, ADR-VL-15; `Refiner`/`Search*Tool` как таковые не делаем), унифицированная модель оффера, гейтвеи-адаптеры, ~~circuit breaker~~ (ADR-VL-04 — отменён), кэш, ~~очередь~~ (ADR-VL-15 — отменена), сессии, планировщики, `RestControllerAdvice`-перехват, метрики/алерты, web-слой, request-id, health.

### Стек (зафиксирован в `pom.xml`)

Java 21 (платформенные потоки, виртуальные — отменены ADR-VL-15), Spring Boot 4.1.1 (`starter-web`, `starter-validation`), OkHttp 5.5.0, Jsoup 1.17.2, Jackson 3 (`tools.jackson`), Lombok.

## 2. Контракты высокого уровня

1. **Изоляция**: источник никогда не пробрасывает исключение наружу из инструмента — всегда `PrincipalResult` (успех/ошибка/partial/timeout).
2. **[ВЛ] Синхронный вызов источников (ADR-VL-15)**: N источников вызываются **последовательно**, в порядке от LLM; каждый **начатый** доводится до конца (ограничен таймаутом вызова), упавший не отменяет остальные и попадает в `warnings`. Отмены нет (ADR-VL-09, ПРОТ-01/02). **[ВЛ] Бюджет времени (ADR-VL-16)**: источники и LLM делят 40 с, потолок всего запроса — 60 с → `REQUEST_TIMEOUT`/504.
3. **Целостность**: после сборки результаты immutable; дедупликация по глобальному `offerId`. ~~сессия атомарна (ветка refine мержится)~~ — **[ОТМЕНЕНО]** (ADR-VL-03): сессия = история реплик в `session_message`.
4. **Перехват**: все исключения проходят через единый `GlobalExceptionHandler`; `ErrorCode` однозначно маппится в (HTTP-статус, пользовательское сообщение, метрика).
5. **request-id** пробивается от HTTP-запроса через MDC в логи каждого источника.
6. **Приоритет флагов**: ~~`circuit > enabled > queue`~~ → **[ОТМЕНЕНО]**: circuit нет; очередь отменена (ADR-VL-15); остаётся порядок `draining` → `enabled` (ADR-VL-04/021).

## 3. Целевая структура пакетов

```
com.workspace.storm.event
├── Application, AppConfig, OkHttpProperties, StormMetrics
├── dto/
│   ├── agent/            search/refine/intent/criteria/response, AgentStatus
│   ├── offer/            Offer, TransportOffer, EventOffer, HotelOffer, ComboOffer, Price, GeoPoint
│   └── source/           SourceQuery, PrincipalResult, SourceMeta, SourceStatus
├── gateway/
│   ├── SourceGateway.java            (базовый контракт)
│   ├── TransportGateway.java
│   ├── EventGateway.java
│   ├── HotelGateway.java
│   ├── atlas/AtlasGateway.java
│   ├── bzd/BzdGateway.java
│   ├── ticketbus/TicketBusGateway.java
│   ├── ticketpro/TicketProGateway.java
│   └── belhotel/BelHotelGateway.java
├── normalize/
│   ├── OfferNormalizer.java          (валюты, копейки, диапазоны, время, дедуп-ключ)
│   └── source/ AtlasOfferMapper, BzdOfferMapper, TicketBusOfferMapper, TicketProOfferMapper, BelHotelOfferMapper
├── tool/
│   ├── SearchBusesTool.java
│   ├── SearchTrainsTool.java
│   ├── SearchEventsTool.java
│   └── SearchHotelsTool.java
├── orchestration/
│   ├── SearchOrchestrator.java       (воркфлоу промпт→ответ)
│   ├── SourceExecutor.java           (последовательный вызов источников, таймауты общего клиента; ADR-VL-15)
│   ├── ResultCollector.java
│   ├── Combiner.java
│   └── Ranker.java
├── agent/
│   ├── Extractor.java                (промпт→SearchIntent)
│   ├── Validator.java
│   ├── Clarifier.java
│   ├── Refiner.java
│   └── Summarizer.java
├── llm/
│   ├── LlmGateway.java, LlmRequest, LlmResponse, LlmProperties   (OpenRouter, function calling)
│   ├── AgentLoop.java, ToolSpec, ToolCall                              (ADR-VL-03)
│   └── RuleBasedFallback.java                                        (работает без ключа)
├── ~~circuit/~~   [ОТМЕНЕНО] CircuitBreaker/State/Registry/Policy (ADR-VL-04)
├── cache/
│   ├── CacheManager.java, CachedResult<T>
├── session/
│   ├── SessionStore.java, Session, SessionProperties, SessionMessageRepository (ADR-VL-02)
├── ~~queue/~~   [ОТМЕНЕНО] RequestQueue/RequestTask/QueueProperties/BackpressurePolicy (ADR-VL-15 — очереди нет)
├── scheduler/
│   ├── WarmupScheduler.java, CacheRefreshScheduler.java, SessionHousekeeper.java
│   ├── ~~CircuitScheduler.java~~   [ОТМЕНЕНО] (ADR-VL-04)
├── exception/
│   ├── StormException.java, ClientException, ServiceException, ParseException, ToolException, LlmException, ErrorCode.java
├── web/
│   ├── AgentController.java (POST /api/v1/agent/chat → text/plain), SourcesAdminController.java, HealthController.java
│   ├── DiagnosticsController.java  (за X-Api-Key, ADR-VL-10)
├── handler/
│   ├── GlobalExceptionHandler.java, RequestIdFilter.java, SourceFallbackHandler.java
└── metrics/
    ├── MetricsCollector.java, AlertEvaluator.java
```

## 4. API-слой и JSON-контракты

### 4.1 Эндпоинты

| Метод и путь | Тело запроса | Ответ | Назначение |
|---|---|---|---|
| `POST /api/v1/agent/chat` | `{sessionId?, text}` | **`text/plain`** (строка) | **Единственный** эндпоинт MVP: реплика → агентный цикл → текст (ADR-VL-01/018/019) |
| ~~`POST /api/v1/agent/refine`~~ | ~~`RefineRequest`~~ | ~~`RefineResponse`~~ | **[ОТМЕНЕНО]** ADR-VL-01: уточнения — обычной репликой в чат |
| ~~`GET /api/v1/agent/sessions/{sessionId}`~~ | — | ~~`SearchResponse`~~ | **[ОТМЕНЕНО]** ADR-VL-01 |
| `GET /api/v1/sources` | — | `[SourceStatus]` | Состояние источников (`enabled`/`draining`) — за `X-Api-Key` (ADR-VL-10) |
| `POST /api/v1/sources/{name}/enable` | — | `SourceStatus` | Ручное включение — за `X-Api-Key` (ADR-VL-10) |
| `POST /api/v1/sources/{name}/disable` | — | `SourceStatus` | Ручное выключение (drain) — за `X-Api-Key` (ADR-VL-10) |
| `GET /api/v1/health/ready` | — | `{status, deps}` | Readiness |
| `GET /api/v1/health/live` | — | `{status}` | Liveness |
| `GET /api/v1/diagnostics/**` | — | — | Диагностика источников — за `X-Api-Key` (ADR-VL-10); ~~диагностика очереди~~ — **[ОТМЕНЕНО]** ADR-VL-15 |

[ВЛ] Ответ агента — **обычный текст** (`text/plain`), поэтому `requestId` наружу не отдаём, но всегда пишем в MDC/логи (ADR-VL-01). Валидация входа — jakarta (`@NotBlank`), как в существующих DTO. ~~Все ответы содержат `requestId` (JSON)~~ → **[ОТМЕНЕНО]** вместе с JSON-контрактом.

### 4.2 ~~JSON `POST /api/v1/agent/search`~~ — ОТМЕНЕНО (ADR-VL-01)

[ВЛ] **Актуальный контракт MVP** — `POST /api/v1/agent/chat`:
- тело: `{ "sessionId": "опционально", "text": "Минск — Брест на 15 июня" }`;
- ответ: `200`, `Content-Type: text/plain`, тело — текст ответа агента (упоминает в том числе упавшие источники);
- `sessionId` создаётся при первом обращении, TTL **15 мин** с последнего обращения (ADR-VL-02);
~~переполнение очереди: `503` + `Retry-After: 5`~~ — **[ОТМЕНЕНО]** ADR-VL-15: очереди нет, такой ошибки не бывает;
- ошибки источников **не** превращаются в HTTP-ошибку — они в тексте (ADR-011).

Ниже — старый JSON-контракт, оставленный как справочный материал:

Запрос:

```json
{
  "requestId": "7f3c...",
  "text": "Автобус Минск — Брест на 15 июня, один",
  "sessionId": null,
  "lang": "ru",
  "deadlineMs": 15000,   // [ВЛ] ОТМЕНЕНО как ограничение: advisory-значение (ADR-VL-09)
  "maxOffers": 20
}
```

Ответ (полный):

```json
{
  "requestId": "7f3c...",
  "sessionId": "s-9ab1...",
  "createdAt": "2026-09-23T10:15:00Z",
  "requiresClarification": false,
  "intent": {
    "domains": ["BUS"],
    "from": { "name": "Минск", "resolvedById": "atlas:station:..." },
    "to": { "name": "Брест", "resolvedById": "atlas:station:..." },
    "dates": [{ "date": "2026-06-15", "slot": "ANY" }],
    "passengers": { "adults": 1, "children": 0 },
    "roundTrip": false,
    "prefs": { "sort": "CHEAPEST", "window": "ANY", "budget": null, "directOnly": false }
  },
  "groups": [
    {
      "domain": "BUS",
      "offers": [
        {
          "id": "atlas:ride:12345",
          "domain": "BUS",
          "source": "atlas",
          "from": { "name": "Минск" },
          "to": { "name": "Брест" },
          "departure": "2026-06-15T08:20:00+03:00",
          "arrival": "2026-06-15T11:00:00+03:00",
          "price": { "amount": 25.50, "currency": "BYN" },
          "seats": 12,
          "link": "https://atlasbus.by/ride/12345",
          "attributes": { "class": "GROUND", "direct": true, "stale": false }
        }
      ],
      "sourceStatuses": [
        { "source": "atlas", "state": "OK", "resultCount": 5 },
        { "source": "ticketbus", "state": "OK", "resultCount": 2 }
      ],
      "warnings": []
    }
  ],
  "combos": [
    {
      "id": "combo:b3:1",
      "score": 0.91,
      "items": [
        { "domain": "BUS", "offerId": "atlas:ride:12345", "arrival": "..." , "price": 25.50 },
        { "domain": "HOTEL", "offerId": "belhotel:hotel:88", "price": 104.0 }
      ],
      "price": { "amount": 129.50, "currency": "BYN" },
      "tags": ["отель рядом с venue", "direct"]
    }
  ],
  "summary": "Прямой автобус Минск — Брест в 08:20 за 25.50 BYN..."
}
```

~~`POST /api/v1/agent/refine`~~ — **[ОТМЕНЕНО]** (ADR-VL-01/019): уточнения приходят обычной репликой в `chat`, LLM сам решает, какие инструменты вызвать. Ниже — справка по старому контракту.

### 4.3 JSON ошибки (единый формат)

```json
{
  "requestId": "7f3c...",
  "code": "SOURCE_UNAVAILABLE",
  "httpStatus": 200,
  "message": "Atlas временно недоступен",
  "source": "atlas",
  "domain": "BUS",
  "partial": true,
  "retryAfterMs": 5000
}
```

Коды ошибок (`ErrorCode`): `BAD_REQUEST, VALIDATION_FAILED, INTENT_NOT_RECOGNIZED, CLARIFICATION_REQUIRED, SOURCE_UNAVAILABLE, SOURCE_TIMEOUT, SOURCE_AUTHORIZATION, SOURCE_RATE_LIMITED, SOURCE_PARSE_ERROR, CIRCUIT_OPEN, ALL_SOURCES_UNAVAILABLE, LLM_UNAVAILABLE, REQUEST_TIMEOUT, SESSION_EXPIRED, QUEUE_REJECTED, TOO_MANY_REQUESTS, INTERNAL_ERROR` (**17** кодов). [ВЛ] `CIRCUIT_OPEN` и `QUEUE_REJECTED` остаются в enum, но **не выдаются** (ADR-VL-04, ADR-VL-15). `REQUEST_TIMEOUT` добавлен ADR-VL-16 — единственный код, означающий не частичный результат источника, а **весь запрос** не уложился в 60 с.

Важно: HTTP-статус ошибки **частичного/пустого источника — 200** (`partial=true` и список предупреждений), т.к. сессия не падает. 4xx/5xx наружу уходят только для `BAD_REQUEST`, `TOO_MANY_REQUESTS(429)`, `INTERNAL_ERROR(500)`, `SESSION_EXPIRED(409)`, `REQUEST_TIMEOUT(504)`. ~~`QUEUE_REJECTED(503)`~~ — **[ОТМЕНЕНО]** ADR-VL-15.

[ВЛ] **Отличие `SOURCE_TIMEOUT` от `REQUEST_TIMEOUT`** ([ADR-VL-16](decisions.md#adr-vl-16)): источник не ответил в отведённый ему остаток бюджета → `SOURCE_TIMEOUT` и **200** с предупреждением, остальные источники и LLM работают дальше. Весь запрос не уложился в 60 с → `REQUEST_TIMEOUT` и **504**: это единственная ошибка, которая **не** возвращает частичный результат, даже если офферы уже собраны (решение владельца: «выдать ошибку клиенту»).

### 4.4 Унифицированная модель оффера

```java
public record Offer(
        String id,                 // "source:kind:externalId" — гарантия уникальности
        String domain,             // BUS | TRAIN | EVENT | HOTEL
        String source,
        GeoPoint from, GeoPoint to,
        Instant departure, Instant arrival,
        Price price,               // amount + currency (нормализовано в BYN + исходная)
        Integer seats,
        String link,
        Map<String, Object> attributes) {
    public static OfferKey key() { ... }   // нормализованный ключ для дедупликации
}
```

## 5. Слой гейтвеев

### 5.1 Базовый контракт

```java
public interface SourceGateway {
    String sourceId();                      // [ВЛ] "atlasbus", "ticketbus", "bzd", "ticketpro", "belhotel"
    SourceKind kind();                      // BUS | TRAIN | EVENT | HOTEL
    // [ВЛ] int priority() — ОТМЕНЕНО (ADR-VL-05): порядок результатов = порядок завершения
    boolean supportsSuggest();              // есть ли справочник станций/городов
}
```

Гейтвей — это **адаптер** между существующим `Client/Service/Parser` и унифицированной моделью. Он:
1. принимает унифицированный `SourceQuery` (from/to/dates/pax/домен);
2. маппит в источник-специфичный DTO (`AtlasSearchRequest`, `TicketBusSearchRequest`, `HotelSearchRequest` или параметры `BzdClient`);
3. вызывает существующий сервис/клиент;
4. нормализует сущности (`AtlasRide`, `BzdTrain`, `TbRace`, `Event`, `Hotel`) через `OfferNormalizer`;
5. возвращает `PrincipalResult<List<Offer>>`.

```java
public final class PrincipalResult<T> {
    Status status;            // OK | FAILURE | PARTIAL | TIMEOUT | SKIPPED(CIRCUIT_OPEN)
    T data;                   // null при FAILURE
    List<String> errors;      // сообщения источника/парсера
    boolean stale;            // из кэша
    long durationMs;
    String sourceId;
}
```

### 5.2 Доменные интерфейсы

```java
public interface TransportGateway extends SourceGateway {
    PrincipalResult<List<Offer>> search(TransportQuery q);
    PrincipalResult<List<StationSuggestion>> suggest(String query);
}
public interface EventGateway extends SourceGateway {
    PrincipalResult<List<Offer>> search(EventQuery q);
}
public interface HotelGateway extends SourceGateway {
    PrincipalResult<List<Offer>> search(HotelQuery q);
}
```

`TransportQuery { from, to, date, passengers, window, directOnly, roundTrip }`.

### 5.3 Карта адаптеров к существующему коду

| Гейтвей | Client/Service | Parser | Маппинг entity → Offer |
|---|---|---|---|
| `AtlasGateway` | `AtlasClient.search/suggestStations` | `AtlasSseParser`, `AtlasStationParser` | `AtlasRide` → transport `Offer`; `rideId` → ключ; `partial`/`errors` из `AtlasSearchResult` |
| `BzdGateway` | `BzdClient.resolveStations + searchRoute` | `BzdStationParser`, `BzdRouteParser` | `BzdTrain` → транспортный `Offer`; `number+fromTime+toTime` → ключ; warmup через `BzdClient` |
| `TicketBusGateway` | `TicketBusClient.resolveStations/searchRaces/routeSchedule` | `TicketBusStationParser`, `TicketBusRaceParser` | `TbRace` → `Offer`; `code+route` → ключ |
| `TicketProGateway` | `TicketProClient.get` | `TicketProEventParser` | `Event`+`Offer` → event `Offer`; `url` → ключ |
| `BelHotelGateway` | `BelHotelClient.search` | `BelHotelResponseParser` | `Hotel`+`RoomOffer` → hotel `Offer`; `id+roomType` → ключ |

### 5.4 OfferNormalizer

Отвечает за: цену (копейки BZD ÷100, диапазон → min, строка → `BigDecimal`, валюта по умолчанию BYN), время (12h/24h, локаль, TZ Europe/Minsk), null-обязательные поля → элемент отбрасывается, дедуп-ключ, `Price` с исходной и нормализованной валютой.

## 6. Оркестрация: инструменты и последовательный вызов источников

### 6.1 Инструменты

```java
@Component
public class SearchBusesTool {
    public ToolResult search(SearchIntent intent) {
        // список гейтвеев: atlasbus, ticketbus
        // [ВЛ] forceSource и budget на источник — ОТМЕНЕНО (ADR-VL-03): инструменты выбирает LLM,
        // выбранные источники вызываются ПОСЛЕДОВАТЕЛЬНО, каждый доводится до конца (ADR-VL-15)
        // PrincipalResult по каждому → ToolResult { offers, warnings }
    }
}
```
`SearchTrainsTool` — только `bzd`; `SearchEventsTool` — `ticketpro`; `SearchHotelsTool` — `belhotel`.

`ToolResult` контракт:
```java
public record ToolResult(
        String domain,
        List<Offer> offers,
        List<PrincipalResult<?>> sourceResults,   // для метрик и статусов
        List<String> warnings,                    // пользователю: «Atlas временно недоступен»
        long durationMs) {}
```

### 6.2 Контракт вызова источников (SourceExecutor)

- **[ВЛ] Синхронно, в текущем потоке** (ADR-VL-15): список `SourceCall` обходится по порядку, каждый вызов выполняется в том же потоке, что и HTTP-запрос. Виртуальных потоков, `ExecutorService` и `CompletableFuture` нет (ADR-009 заменён).
- **[ВЛ] Таймауты**: **один** `OkHttpClient` на всё приложение, один набор базовых таймаутов из `OkHttpProperties`: connect 5s, read 10s, write 10s, `callTimeout` = 60 000 (в коде сейчас `0` = бесконечно — **исправить**). ~~per-source оверрайды через `newBuilder()`, SSE-пул~~ — **ОТМЕНЕНО** (ADR-008 заменён). Фактический таймаут каждого вызова считается от остатка бюджета — см. «Бюджет времени запроса» ниже.
- **[ВЛ] Бюджет времени запроса** ([ADR-VL-16](decisions.md#adr-vl-16)): `RequestContext` несёт `budgetDeadlineAt = start + 40 с` и `hardDeadlineAt = start + 60 с` (монотонное время). Перед каждым вызовом:
  - `remaining = budgetDeadlineAt − now`; если `remaining ≤ minSourceCallMs` (1 с) → вызов **не делается**, `SKIPPED_NO_BUDGET`, метрика `source.skipped`, предупреждение пользователю (ADR-VL-07);
  - иначе таймаут **конкретного вызова** = `remaining`, через `client.newCall(request).timeout().timeout(remaining, MILLISECONDS)` — в OkHttp 5 `Call.timeout()` пер-вызововый, второй клиент не нужен;
  - после источников LLM получает `min(remaining40, remaining60, llmTimeoutMs=30 c)`;
  - к `start + 60 с` запрос не завершён → `REQUEST_TIMEOUT` → **504** (даже при собранных офферах).
- **Доводим до конца** [ВЛ]: каждый **начатый** вызов доводится до успеха или своего таймаута; упавший не отменяет остальные и не теряет уже собранное. **Отмены нет** — ни `cancel()`, ни `AbortController`, ни обрыва уже идущего запроса (ADR-VL-09). Потолок 60 с гарантирован конструкцией: мы просто не начинаем вызов, который в бюджет не влезает. `deadlineMs` из запроса, если передан, — **advisory**: пишем в лог/метрику, бюджет от него не растёт.
- **Один источник — одно исключение**: гейтвей сам ловит всё и превращает в `PrincipalResult` (включая таймаут); наружу утекает только `InterruptedException` при shutdown.
- **Skip** [ВЛ]: если `draining=true` или `enabled=false` — вызов **не делается**, результат `SKIPPED`, метрика `source.skipped`. ~~`circuit OPEN`~~ — **ОТМЕНЕНО** (ADR-VL-04).
- **Порядок** [ВЛ]: результаты **в порядке вызова**; приоритетов и сортировки по `priority()` нет (ADR-VL-05/ADR-VL-15).
- **Цена решения**: внутри бюджета источники могут не успеть — тогда часть вызовов пропускается, а ответ упирается в 60 с. Поэтому таймауты короткие, кэш обязателен (TTL 5 мин), а дешёвые источники ставятся в порядок вызова первыми.

```java
public class SourceExecutor {
    <T> List<PrincipalResult<T>> execute(List<SourceCall<T>> calls, RequestContext ctx);
    // последовательно, в текущем потоке; таймаут каждого вызова = остаток бюджета (ADR-VL-16);
    // при remaining <= 1с — SKIPPED_NO_BUDGET; отмены нет; порядок = порядок вызова
}
```

```java
public record RequestContext(
        String requestId,
        Instant startedAt, long budgetDeadlineAt,  // +40 с — мягкий бюджет
        long hardDeadlineAt,                      // +60 с — потолок, REQUEST_TIMEOUT/504
        String lang,
        Long advisoryDeadlineMs) {}

// типичный вызов источника внутри SourceExecutor
long remaining = ctx.budgetDeadlineAt() - System.nanoTime() / 1_000_000;
if (remaining <= minSourceCallMs) return PrincipalResult.skippedNoBudget(call.source());
Call call = httpClient.newCall(request);
call.timeout().timeout(remaining, TimeUnit.MILLISECONDS);   // пер-вызововый таймаут, клиент не клонируем
return gateway.execute(call);
```

### 6.3 Combiner и Ranker

- `Combiner`: склейка доменов (BUS+EVENT+HOTEL, roundTrip, multi-direction), фильтр по бюджету, дедупликация по `Offer.id`, расчёт `ComboOffer` (суммы, дистанции, «отель рядом с venue» по координатам ≤1 км).
- `Ranker`: сортировка по `intent.prefs.sort` (`CHEAPEST|FASTEST|BEST`), top-N = `maxOffers`.
- Нормализация валют для сравнения: BYN как базовая (котировки в Constants), пометка `currency.unknown`.

## 7. Система исключений и перехват

### 7.1 Иерархия

Существующие `*ClientException` и `*ServiceException` **сохраняются**, но получают общего предка:

```
StormException (abstract)                         — несёт ErrorCode errorCode, String source, без requestId внешне
 ├── ClientException          ← AtlasClientException, BzdClientException,
 │                              TicketBusClientException, TicketProClientException, BelHotelClientException
 ├── ServiceException         ← AtlasServiceException, TicketBusServiceException, TicketProServiceException
 ├── ParseException                              — parser/selector не нашёл данных; может нести partial-данные
 ├── ToolException                               — ошибка инструмента (уже wrapped PrincipalResult)
 └── LlmException                                — сбой провайдера LLM
```

Новые правила:
- `StormException` хранит `ErrorCode` и `source`, а логгер внешнего слоя берёт request-id из MDC.
- `@ResponseStatus` на классах исключений **убирается**: статус навешивает `GlobalExceptionHandler` по `ErrorCode` (иначе `BAD_GATEWAY` протекал бы наружу).
- Парсеры бросают `ParseException(message, partialResult?)` — не `IllegalArgumentException` на пустом SSE.

### 7.2 Маппинг ошибка → ErrorCode → ответ

| Ситуация (каталог 1–235) | Исключение/флаг | ErrorCode | HTTP | Пользовательское сообщение |
|---|---|---|---|---|
| 500/502 источника | `ClientException` | `SOURCE_UNAVAILABLE` | 200 (partial) | источник временно недоступен |
| 503 + Retry-After | `ClientException` + retryAfter | `SOURCE_UNAVAILABLE` | 200 | источник временно недоступен |
| 504 / read/write timeout / SSE-завис | `ClientException(timeout)` | `SOURCE_TIMEOUT` | 200 | источник долго не отвечает |
| 429 | `ClientException` + retryAfter>5s | `SOURCE_RATE_LIMITED` | 200 | источник временно недоступен |
| 403 / капча | `ClientException` | `SOURCE_AUTHORIZATION` | 200 | источник временно недоступен (breaker не открываем, ADR-VL-04) |
| 401 после warmup | retry warmup → fail | `SOURCE_AUTHORIZATION` | 200 | источник недоступен (сессия) |
| 404 / редирект домена | `ClientException` | `SOURCE_UNAVAILABLE` + metric/alert | 200 | источник недоступен |
| смена формата / 0 элементов | `ParseException` | `SOURCE_PARSE_ERROR` + alert | 200 | не удалось получить данные |
| Promise «все источники лежат» | нет данных вовсе | `ALL_SOURCES_UNAVAILABLE` | 200 | Сервис временно недоступен |
| LLM не смог | `LlmException` | `INTENT_NOT_RECOGNIZED` / `LLM_UNAVAILABLE` | 200 | Не могу разобрать запрос, попробуйте позже |
| невалидный запрос | validation | `BAD_REQUEST` | 400 | сообщение валидатора |
| ~~очередь переполнена~~ | ~~`queue.reject`~~ | ~~`QUEUE_REJECTED`~~ | ~~503~~ | **[ОТМЕНЕНО]** ADR-VL-15: очереди нет |
| сессия протухла | — | `SESSION_EXPIRED` | 409 | Сессия истекла, начните заново |
| **весь запрос не уложился в 60 с** [ВЛ] | `TimeoutException` (SoftTimeout) / проверка `hardDeadlineAt` | **`REQUEST_TIMEOUT`** | **504** | Сервис не успел ответить, попробуйте позже |
| нераспознанный интент | `Validator` | `CLARIFICATION_REQUIRED` | 200 | Уточните город/дату |
| внутренняя ошибка | утечка | `INTERNAL_ERROR` | 500 | Внутренняя ошибка, попробуйте позже |

### 7.3 GlobalExceptionHandler (@RestControllerAdvice)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(StormException.class)   String handle(StormException e);   // по e.errorCode → текст
    @ExceptionHandler(MethodArgumentNotValidException.class)  // → BAD_REQUEST, список полей
    @ExceptionHandler(Exception.class)         // → INTERNAL_ERROR, полный стек в лог, к клиенту — без деталей
    @ExceptionHandler(TimeoutException.class)  // → REQUEST_TIMEOUT, 504 (ADR-VL-16), НЕ partial
}
```
[ВЛ] Ответ — **текст**, поэтому `requestId` в тело ответа **не кладём** (ADR-VL-01): `requestId` берётся логгером из MDC (ADR-011). Все хендлеры: инкрементят метрику, пишут в лог с `[%X{requestId}]`, наружу стектрейс не светят. ~~кладут `requestId` в ответ~~ — **[ОТМЕНЕНО]** вместе с JSON-конвертом.

### 7.4 RequestIdFilter

- `OncePerRequestFilter`: читает `X-Request-Id` (если есть и проходит валидацию: не пустой, длина ≤ 64, символы `[A-Za-z0-9._-]`), иначе генерирует UUID; кладёт значение в **MDC** под ключом `requestId` и в атрибут запроса; в `finally` **очищает MDC** — Tomcat переиспользует потоки, иначе ID «утечёт» в следующий запрос.
- Тело ответа не меняет; `requestId` используется в `request_log`/`source_error_log`/`session_message` (ADR-011).
- **Идемпотентность фильтр не проверяет** [ВЛ]: дедупликация по `requestId` — `INSERT … ON CONFLICT DO NOTHING` в таблице `idempotency` (ADR-001, шаг 187), то есть в репозитории/сервисе, а не в web-слое. ~~При `POST /search` также проверяет идемпотентность~~ — **[ОТМЕНЕНО]**: эндпоинта `/search` не существует (ADR-VL-01).

## 8. Каталог сообщений ошибок

Файл `src/main/resources/messages.properties` (+ `messages_en.properties`), ключи — «код → текст» для пользователя:

```
source.unavailable=источник временно недоступен
source.timeout=источник долго не отвечает
all.sources.unavailable=Сервис временно недоступен
city.not_found=Город не найден, уточните название
station.ambiguous=Уточните станцию: Минск-Пассажирский, Минск-Восточный, ...
station.not_found=Станция не найдена
tickets.90days=Билеты продаются за 90 дней
hotel.not_found_near=Отелей рядом не нашлось
dates.required=Уточните даты поездки
where.clarify=Где именно?
session.expired=Сессия истекла, начните заново
llm.unavailable=Не могу разобрать запрос, попробуйте позже
internal.error=Внутренняя ошибка, попробуйте позже
disclaimer.payment=Оплата производится на сайте источника; карты некоторых стран могут не приниматься
disclaimer.visa=Проверьте визовые требования заранее
```

Правило: **любой** ветки unhappy-пути (каталог 1–235) хватает одного из этих сообщений + `partial`-офферы; ни одна не роняет сессию.

## 9. Планировщики (Schedulers)

Все — `@Component` + `@EnableScheduling` в `Application`. Запускаются в отдельном singleton-пуле `ThreadPoolTaskScheduler(2)`; не блокируют обработку запросов.

| Планировщик | Период | Действие |
|---|---|---|
| `WarmupScheduler` | при старте (`initialDelay=0`) + повтор 5 мин | `bzd`, `ticketbus` warmup; фиксирует cookies/PHPSESSID; single-flight (`synchronized` как в `TicketBusClient.warmup`) |
| ~~`CircuitScheduler`~~ | — | **[ОТМЕНЕНО]** (ADR-VL-04): переходов OPEN/HALF_OPEN нет |
| `CacheRefreshScheduler` | 5 мин | обновляет справочники городов (suggest) и тёплый кэш результатов (АНП-95, АНП-113) |
| `SessionHousekeeper` | 1 мин | [ВЛ] вычищает истёкшие сессии (**TTL 15 мин** с последнего обращения, ADR-VL-02), каскадом удаляет `session_message` |
| ~~`QueueMonitor`~~ | — | **[ОТМЕНЕНО]** (ADR-VL-15): очереди нет, мониторить нечего |

Порядок старта: кэш → warmup ~~→ circuit~~ ~~→ очередь~~ (circuit нет — ADR-VL-04, очереди нет — ADR-VL-15).

## 10. ~~Circuit breaker (детали реализации)~~ — ОТМЕНЕНО (ADR-VL-04, ПРОТ-17)

Класс `CircuitBreaker`, `CircuitState(CLOSED/OPEN/HALF_OPEN)`, `CircuitRegistry`, `CircuitPolicy`, таблица `circuit_state` и `CircuitScheduler` **в MVP не реализуются** (ПРОТ-17/18). Вместо них:
- per-source таймауты (ADR-013) — источник не может висеть бесконечно;
- `MetricsCollector` + `AlertEvaluator` (WARN/ERROR) — видно, что источник деградировал (ADR-VL-06);
- ручной флаг `source_state.enabled` + `draining` — оператор сам решает, когда отключить (ADR-VL-10);
- `403` → сообщение + **без retry**; `401` → один warmup-retry; `429`/`503` → ошибка сразу, **без пауз и без retry** (ADR-011/016);
- retry одного логического запроса = **одна** ошибка в метриках (ADR-016);
- кэш (TTL 5 мин) отдаётся только вместе с предупреждением пользователю.

Ниже — спецификация breaker'а, оставленная как справочный материал для будущей версии:

```java
public final class CircuitBreaker {
    CircuitState state;                 // CLOSED/OPEN/HALF_OPEN (volatile)
    AtomicLong failureCount;
    AtomicLong openedAt;                // epoch ms
    long failureThreshold = 5;          // ошибок за окно 30 сек
    long openTimeoutMs = 60_000;        // TTL OPEN
    long halfOpenPermits = 1;           // пробный запрос (AtomicBoolean)
    CircuitKey key;                     // "atlasbus:stream", "atlasbus:suggest", "llm" (АНП-97)
}
```

## 11. Кэш и сессии

`CacheManager`: Caffeine-компоненты (добавить зависимость) или `ConcurrentHashMap + TTL` — на выбор, контракт:
```java
public final class CachedResult<T> {
    T value; Instant cachedAt; boolean stale; SourceKey key;
}
```
- ключ кэша: `sourceId + endpoint + параметры-нормализованные`;
- TTL: справочники 1 день, результаты 5 мин (АНП-95);
- [ВЛ] при ошибке/таймауте источника — отдаём кэш **только вместе с предупреждением** пользователю; ~~`stale=true` при OPEN~~ — circuit нет (ADR-VL-04).

[ВЛ] `SessionStore` — **Postgres** (ADR-VL-02), in-memory/Redis **не используем**:
```java
public final class Session {
    String id; Instant createdAt; Instant lastAccessAt; long ttlMinutes;  // ttl = 15 мин
}
public record SessionMessage(long id, String sessionId, String role, String text,
                             String requestId, Instant createdAt) {}
```
- реплики пользователя и агента пишутся в `session_message`; контекст LLM = последние **20** сообщений (ADR-VL-02/019);
- `touch()` продлевает TTL при каждом обращении; истёкшая сессия → `SESSION_EXPIRED` (новый `sessionId`);
- `state ∈ {NEW, ACTIVE, EXPIRED}`; **`CANCELLED` не делаем** — отмены нет (ADR-VL-09);
- ~~`Refiner` мержит `intent`/`steps`~~ — **[ОТМЕНЕНО]** (ADR-VL-03): уточнения приходят репликами, LLM выбирает инструменты сам.

## 12. ~~Очередь~~ — ОТМЕНЕНО (ADR-VL-15)

`RequestQueue` (`ArrayBlockingQueue(100)`), `RequestTask`, `QueueProperties`, `BackpressurePolicy`, consumer pool 4–8, дедупликация по `requestId` в очереди, `QUEUE_REJECTED` (503) и `Retry-After` — **в MVP не реализуются**. Запрос обрабатывается в потоке HTTP-запроса: контроллер → `SearchOrchestrator` → инструменты **последовательно** → ответ. Backpressure обеспечивает сам Tomcat числом потоков; дедупликация `requestId` делает таблица `idempotency` (ADR-001), аудит — `request_log`.

## 13. Метрики, алерты, health, логи

- `MetricsCollector` — инкрементальные счётчики с тегами:
  - `source.errors{source, code}` — на каждую ошибку источника;
  - `source.latency{source}_ms{histogram}`; `source.skipped{source}` (в т.ч. `SKIPPED_NO_BUDGET` — не хватило бюджета времени, ADR-VL-16);
  - ~~`source.circuit{source}=open`~~ — **[ОТМЕНЕНО]** (ADR-VL-04/022); имена метрик — по словарю в `25_contradictions.md` (ПРОТ-20);
  - `parse.errors{source}`; `all.sources.disabled`; `request.timeout` (ADR-VL-16: сработал потолок 60 с); ~~`queue.length`, `queue.rejected`~~ — **[ОТМЕНЕНО]** (ADR-VL-15: очереди нет);
  - `session.active`, `llm.errors{code}`.
- `AlertEvaluator` — локальные пороги (без Prometheus, чтобы не добавлять зависимость): `parse.errors > 10/мин` → лог WARN + webhook-заглушка; 404/редирект домена → alert «обновить Constants»; SSL → alert «проверить сертификат»; капча → alert «анти-бот».
- Health: `ready` проверяет БД и «все источники отключены/недоступны»; `live` — только JVM. ~~проверку очереди~~ — **[ОТМЕНЕНО]** (ADR-VL-15).
- Логи: MDC `requestId`, `source`, `code`, `status`; сообщение = тип исключения + URL + HTTP-код + partial/timeout-флаги.

## 14. Контракты целостности

1. **Идемпотентность**: `requestId` уникален на (пользователь, короткое окно); повторный `requestId` обрабатывается один раз — гарантирует таблица `idempotency` (`INSERT … ON CONFLICT DO NOTHING`, ADR-001). ~~не попадает в очередь второй раз~~ — **[ОТМЕНЕНО]** (ADR-VL-15). ~~возвращает тот же `SearchResponse`~~ — **[ОТМЕНЕНО]** (ADR-VL-01): ответ — текст, повтор отдаётся из кэша.
2. **Атомарность сессии**: [ВЛ] реплики сессии пишутся последовательно в `session_message`; отдельных refine-веток нет (ADR-VL-01/019).
3. **Завершённость обхода источников**: [ВЛ] `SourceExecutor` последовательно доводит **каждый начатый** вызов до результата или таймаута; «зависший» вызов невозможен — ограничение задаёт таймаут вызова (остаток бюджета, [ADR-VL-16](decisions.md#adr-vl-16)). Отмены и дочерних потоков нет (ADR-VL-09, ADR-VL-15). Источник, которому остатка не хватило, **пропускается** (`SKIPPED_NO_BUDGET`), а не обрывается на середине.
4. **Валидность оффера**: ни один `Offer` с `null` в обязательных полях (domain, from, to, departure, price) не попадает в ответ — режет `OfferNormalizer`.
5. **Дедупликация** single-pass: на входе (парсер) по `rideId`, на выходе (Combiner) по нормализованному `offerId`.
6. **Валютная целостность**: цена сравнивается только в BYN; если валюта неизвестна — оффер помечается `attributes.currencyKnown=false` и не участвует в сортировке по цене.
7. **Данные не теряются при partial**: `partial=true` идёт до пользователя вместе с собранным, а не превращается в пустоту. **Исключение** [ВЛ]: если сработал потолок 60 с — `REQUEST_TIMEOUT`/504 **без** собранного ([ADR-VL-16](decisions.md#adr-vl-16)).

## 15. Порядок реализации (фазы)

Каждая фаза завершается «зелёными» тестами. Существующие тесты (`AtlasClientIntegrationTest`, парсер-тесты и т.д.) не ломаем.

| Фаза | Делаем | Выход |
|---|---|---|
| **0. Каркас** | пул планировщика (без virtual threads — ADR-VL-15), `RequestIdFilter` (с очисткой MDC), `GlobalExceptionHandler`, `ErrorCode` (17 кодов) | web-бейз, health, тест ошибок |
| **1. Исключения** | новая иерархия поверх существующих `*Client/ServiceException`, `ParseException`, карта маппинга | unit-тесты карты, advise-тесты |
| **2. Unified-модель + Normalizer** | `Offer`, `Price`, `GeoPoint`, мапперы с 5 парсеров | unit-тесты нормализации (копейки, валюты, null, диапазоны) |
| **3. Гейтвеи** | 5 адаптеров поверх существующих клиентов | unit (mock клиента) + интеграция по живому источнику |
| **4. ~~Circuit + Registry~~** | **[ОТМЕНЕНО]** (ADR-VL-04) — вместо: per-source таймауты, `source.errors`+алерты, `enabled`/`draining` | unit `SourceErrorCountTest` (retry = 1 ошибка) |
| **5. SourceExecutor + Collector** | последовательный вызов источников, **каждый начатый доводится до конца, без отмены**, таймаут вызова = остаток бюджета, `SKIPPED_NO_BUDGET` при нехватке, порядок вызова | unit-тесты таймаутов вызова, пропуска по бюджету и частичных результатов |
| **6. Tools** | [ВЛ] инструменты-LLM по одному на источник (`search_atlasbus`, `search_ticketbus`, `search_bzd`, `search_ticketpro`, `search_belhotel`, `get_offers`) + ToolResult | тесты: «один источник лежит — второй отдаёт, упавший упомянут в тексте» |
| **7. Combiner + Ranker** | дедуп, комбо, бюджет, сортировка, «отель рядом» | unit-тесты склейки (1–4 домена) |
| **8. LLM-гейт + агентный цикл** | `LlmGateway` (OpenRouter, function calling), `AgentLoop` (maxToolRounds из настроек), `RuleBasedFallback` без ключа | тест агентного цикла на моке LLM, тест фолбэка (АНП-100) |
| **9. Оркестрация + Web** | `SearchOrchestrator`, `AgentController` (`POST /api/v1/agent/chat` → `text/plain`), `SourcesAdminController` (`X-Api-Key`) | интеграционные тесты API (happy+unhappy) |
| **10. ~~Очередь~~, сессии, кэш** | `SessionStore`/`session_message` (Postgres, TTL 15 мин), `CacheManager` (+ housekeepers); очереди нет (ADR-VL-15) | тесты TTL и сессий; ~~backpressure, дедуп в очереди~~ — **[ОТМЕНЕНО]** |
| **11. Метрики, алерты, логи** | `MetricsCollector`, `AlertEvaluator`, MDC-логи | проверка метрик на каждой ошибке каталога |
| **12. Е2Е** | сквозные сценарии по happy/unhappy, каталог 1–235 | отчёт покрытия каталога |

## 16. Как развернуть в план на 1000+ шагов

Для **каждой единицы U** из фаз 0–12 раскрываются одинаковые десятки шагов. Шаблон единицы:

1. Спецификация: сигнатуры, JSON, поля, границы — `U.spec.md` (из этого документа).
2. Файлы: создать пустые каркасы (интерфейс + заглушка), подключить в Spring (bean-граф).
3. Реализация happy-пути `U` (по выбранному объёму).
4. Обработка каждого unhappy-кода из каталога 1–235, относящегося к `U` (исключение → `ErrorCode` → сообщение → метрика → alert).
5. Конфиги: `application.properties` (`SessionProperties`, `OkHttpProperties` (таймауты клиента), `TimeoutProperties` (бюджет 40/60 с — ADR-VL-16), `LlmProperties`/`AgentProperties`). ~~`QueueProperties`~~ — **[ОТМЕНЕНО]** (ADR-VL-15). ~~`CircuitPolicy`~~ — **[ОТМЕНЕНО]** (ADR-VL-04).
6. Юнит-тесты: happy, каждый happy-вариант, каждое исключение, таймауты, null, дедуп.
7. Интеграционные тесты (mock OkHttp `MockWebServer` — добавить в тесты; live-интеграции уже есть).
8. Тесты контрактов API (JSON фикстуры) + тесты сообщений (i18n).
9. Метрика/алерт для каждой ошибки; MDC-логи.
10. Документирование в `docs/`; обновление README-карты.
11. Кросс-проверка: не конфликтует с фазами (FIFO-порядок, deadlock'и). ~~cancel~~ — **[ОТМЕНЕНО]** (ADR-VL-09).
12. Ревью-чеклист + обновление списка известных ограничений.

Пример декомпозиции одной единицы (фрагмент, чтобы таргетировать гранулярность ~30–50 шагов на единицу):
- `AtlasGateway`: MarshalRequest → вызов `AtlasClient.search` → обработка 1…15 HTTP-кодов → `PartialResult` из `AtlasSearchResult.partial` → SSE: события progress/rides/done/error/unknown → нормализация `AtlasRide` → дедуп по `rideId` → маппинг ошибок в `PrincipalResult` → юнит-тесты (mock client) → интеграционный тест (живой) → метрики → alert «смена формата» → контракт-тест JSON.

Итого: 13 фаз × ~10 единиц × ~30–50 шагов ≈ **3000+ строка-шагов**; минимум 1000 достигается уже фазами 1–7.

Условие выхода плана: каждый unhappy-пункт каталога 1–235 имеет ссылку «правило → класс → тест».