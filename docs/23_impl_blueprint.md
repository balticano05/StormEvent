# Блюпринт реализации (нижний уровень): API, гейтвеи, планировщики, обработчики, исключения, параллельность

Назначение документа — быть **исходником для построения детального плана (1000+ шагов)**.
Здесь зафиксированы контракты нижнего уровня: какие пакеты/классы/интерфейсы строить, какие REST-эндпоинты и JSON-структуры, как устроены гейтвеи, планировщики, обработчики, система исключений и перехват, сообщения ошибок, контракт параллельных запросов и целостность данных.

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

LLM-агент (`Extractor/Validator/Clarifier/Refiner/Summarizer`), `Search*Tool`, унифицированная модель оффера, гейтвеи-адаптеры, circuit breaker, кэш, очередь, сессии, планировщики, `RestControllerAdvice`-перехват, метрики/алерты, web-слой, request-id, health.

### Стек (зафиксирован в `pom.xml`)

Java 21 (виртуальные потоки), Spring Boot 4.1.1 (`starter-web`, `starter-validation`), OkHttp 5.5.0, Jsoup 1.17.2, Jackson 3 (`tools.jackson`), Lombok.

## 2. Контракты высокого уровня

1. **Изоляция**: источник никогда не пробрасывает исключение наружу из инструмента — всегда `PrincipalResult` (успех/ошибка/partial/timeout).
2. **Параллельность fan-out/fan-in**: N источников выполняются параллельно, сборка дожидается их до общего **deadline**, а не до самого медленного.
3. **Целостность**: после сборки результаты immutable; дедупликация по глобальному `offerId`; сессия атомарна (ветка refine мержится, а не перезаписывается неявно).
4. **Перехват**: все исключения проходят через единый `GlobalExceptionHandler`; `ErrorCode` однозначно маппится в (HTTP-статус, пользовательское сообщение, метрика).
5. **request-id** пробивается от HTTP-запроса через MDC в логи каждого источника.
6. **Приоритет флагов**: `circuit > enabled > queue`.

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
│   ├── ParallelExecutor.java         (fan-out/fan-in, deadline)
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
│   ├── LlmGateway.java, LlmRequest, LlmResponse, LlmProperties
│   └── RuleBasedFallback.java
├── circuit/
│   ├── CircuitBreaker.java, CircuitState(CLOSED/OPEN/HALF_OPEN), CircuitRegistry, CircuitPolicy
├── cache/
│   ├── CacheManager.java, CachedResult<T>
├── session/
│   ├── SessionStore.java, Session, SessionProperties
├── queue/
│   ├── RequestQueue.java, RequestTask, QueueProperties, BackpressurePolicy
├── scheduler/
│   ├── WarmupScheduler.java, CircuitScheduler.java, CacheRefreshScheduler.java, SessionHousekeeper.java
├── exception/
│   ├── StormException.java, ClientException, ServiceException, ParseException, ToolException, LlmException, ErrorCode.java
├── web/
│   ├── AgentController.java, SourcesAdminController.java, HealthController.java
├── handler/
│   ├── GlobalExceptionHandler.java, RequestIdFilter.java, SourceFallbackHandler.java
└── metrics/
    ├── MetricsCollector.java, AlertEvaluator.java
```

## 4. API-слой и JSON-контракты

### 4.1 Эндпоинты

| Метод и путь | Тело запроса | Ответ | Назначение |
|---|---|---|---|
| `POST /api/v1/agent/search` | `SearchRequest` | `SearchResponse` | Основной поиск (1 промпт → офферы + резюме) |
| `POST /api/v1/agent/refine` | `RefineRequest` | `RefineResponse` | Уточнение: мержится в сессию |
| `GET /api/v1/agent/sessions/{sessionId}` | — | `SearchResponse` | Повторный показ сессии |
| `GET /api/v1/sources` | — | `[{SourceStatus}]` | Состояние источников (enabled/circuit/queue) |
| `POST /api/v1/sources/{name}/enable` | — | `SourceStatus` | Включение (ramp-up) |
| `POST /api/v1/sources/{name}/disable` | — | `SourceStatus` | Выключение (drain) |
| `GET /api/v1/health/ready` | — | `{status, deps}` | Readiness |
| `GET /api/v1/health/live` | — | `{status}` | Liveness |

Все ответы содержат `requestId`. Валидация — jakarta (`@NotBlank`, `@NotNull`, `@AssertTrue`), как в существующих DTO.

### 4.2 JSON `POST /api/v1/agent/search`

Запрос:

```json
{
  "requestId": "7f3c...",
  "text": "Автобус Минск — Брест на 15 июня, один",
  "sessionId": null,
  "lang": "ru",
  "deadlineMs": 15000,
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

`POST /api/v1/agent/refine` — добавляет к запросу search `baseSessionId` и правила уточнения; ответ тот же `SearchResponse` + `refined: true`.

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

Коды ошибок (`ErrorCode`): `BAD_REQUEST, VALIDATION_FAILED, INTENT_NOT_RECOGNIZED, CLARIFICATION_REQUIRED, SOURCE_UNAVAILABLE, SOURCE_TIMEOUT, SOURCE_AUTHORIZATION, SOURCE_RATE_LIMITED, SOURCE_PARSE_ERROR, CIRCUIT_OPEN, ALL_SOURCES_UNAVAILABLE, LLM_UNAVAILABLE, SESSION_EXPIRED, QUEUE_REJECTED, TOO_MANY_REQUESTS, INTERNAL_ERROR`.

Важно: HTTP-статус ошибки **частичного/пустого источника — 200** (`partial=true` и список предупреждений), т.к. сессия не падает. 4xx/5xx наружу уходят только для `BAD_REQUEST`, `QUEUE_REJECTED(503)`, `TOO_MANY_REQUESTS(429)`, `INTERNAL_ERROR(500)`, `SESSION_EXPIRED(409)`.

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
    String sourceId();                      // "atlas", "bzd", "ticketbus", "ticketpro", "belhotel"
    SourceKind kind();                      // BUS | TRAIN | EVENT | HOTEL
    int priority();                         // порядок в параллельном вызове
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

## 6. Оркестрация: инструменты и параллельность

### 6.1 Инструменты

```java
@Component
public class SearchBusesTool {
    public ToolResult search(SearchIntent intent) {
        // список гейтвеев: atlas, ticketbus (+ forceSource/budget/прямые)
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

### 6.2 Контракт параллельных запросов (ParallelExecutor)

- **Fan-out**: на каждый sourceJob (гейтвей + параметры) создаётся задача; выполняется на виртуальном потоке (`Thread.ofVirtual().name("src-<source>", 0).start()`); каждому потоку присваивается свой `OkHttpClient`-таймаут через `OkHttpProperties` (при необходимости per-source таймауты-оверрайды).
- **Per-source лимиты**: connect 5s, read 10s (bzd/ticketbus: 60s для тяжёлых POST), SSE read 10s + max duration 30s, callTimeout 45s, общий deadline запроса = `deadlineMs` из `SearchRequest` (default 15s), бюджет на источники = 90% от deadline.
- **Fan-in**: `ResultCollector` дожидается задач до **deadline**, а не до завершения каждой. Медленный источник после deadline → задача прерывается (`future.cancel(true)`), результат от него `PrincipalResult(TIMEOUT)`.
- **Один источник — одно исключение**: гейтвей сам ловит всё и превращает в `PrincipalResult`; наружу из `ParallelExecutor` утекает только `CancellationException` по таймауту запроса или `InterruptedException` при shutdown.
- **Circuit/skip**: если `circuit OPEN` или `enabled=false` — запрос **не стартует** (задача не создаётся), результат `SKIPPED`, метрика `source.skipped`.

```java
public class ParallelExecutor {
    <T> List<PrincipalResult<T>> fanOut(List<SourceCall<T>> calls, Duration deadline);
    // вызывает каждый SourceCall в отдельном виртуальном потоке,
    // ждёт до deadline, отменяет незавершённые, гарантирует порядок по priority()
}
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
| 403 / капча | `ClientException` | `CIRCUIT_OPEN` (эффект: breaker) | 200 | источник временно недоступен |
| 401 после warmup | retry warmup → fail | `SOURCE_AUTHORIZATION` | 200 | источник недоступен (сессия) |
| 404 / редирект домена | `ClientException` | `SOURCE_UNAVAILABLE` + metric/alert | 200 | источник недоступен |
| смена формата / 0 элементов | `ParseException` | `SOURCE_PARSE_ERROR` + alert | 200 | не удалось получить данные |
| Promise «все источники лежат» | нет данных вовсе | `ALL_SOURCES_UNAVAILABLE` | 200 | Сервис временно недоступен |
| LLM не смог | `LlmException` | `INTENT_NOT_RECOGNIZED` / `LLM_UNAVAILABLE` | 200 | Не могу разобрать запрос, попробуйте позже |
| невалидный запрос | validation | `BAD_REQUEST` | 400 | сообщение валидатора |
| очередь переполнена | `queue.reject` | `QUEUE_REJECTED` | 503 | Сервис перегружен, подождите |
| сессия протухла | — | `SESSION_EXPIRED` | 409 | Сессия истекла, начните заново |
| нераспознанный интент | `Validator` | `CLARIFICATION_REQUIRED` | 200 | Уточните город/дату |
| внутренняя ошибка | утечка | `INTERNAL_ERROR` | 500 | Внутренняя ошибка, попробуйте позже |

### 7.3 GlobalExceptionHandler (@RestControllerAdvice)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(StormException.class)   ErrorResponse handle(StormException e);   // по e.errorCode
    @ExceptionHandler(MethodArgumentNotValidException.class)  // → BAD_REQUEST, список полей
    @ExceptionHandler(Exception.class)         // → INTERNAL_ERROR, полный стек в лог, к клиенту — без деталей
    @ExceptionHandler(CancellationException.class) / TimeoutException  // → 504? нет: SEARCH_FINISHED_PARTIAL
}
```
Все хендлеры: кладут `requestId` в ответ, инкрементят метрику, не светят стектрейс наружу.

### 7.4 RequestIdFilter

- `OncePerRequestFilter`: генерирует/читает `X-Request-Id`, кладёт в MDC, пробрасывает в `PrincipalResult` и `ErrorResponse`.
- При `POST /search` также проверяет идемпотентность по `requestId` (повторный requestId → тот же кэшированный ответ/сессия).

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
queue.rejected=Сервис перегружен, подождите
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
| `CircuitScheduler` | 5 сек | для каждого `CircuitBreaker` в OPEN: по истечении TTL (60 сек, force-макс 5 мин — АНП-96) → HALF_OPEN и 1 пробный запрос |
| `CacheRefreshScheduler` | 5 мин | обновляет справочники городов (suggest) и тёплый кэш результатов (АНП-95, АНП-113) |
| `SessionHousekeeper` | 1 мин | вычищает `Session.ttl` (BZD 30 мин, idle 15 мин), освобождает слоты |
| `QueueMonitor` | 1 сек | метрика длины очереди; если `length > highWaterMark` — `queue.reject=true`; backpressure |

Порядок старта: очередь → кэш → warmup → circuit (чтобы warmup не конкурировал с трафиком).

## 10. Circuit breaker (детали реализации)

```java
public final class CircuitBreaker {
    CircuitState state;                 // CLOSED/OPEN/HALF_OPEN (volatile)
    AtomicLong failureCount;
    AtomicLong openedAt;                // epoch ms
    long failureThreshold = 5;          // ошибок за окно 30 сек
    long openTimeoutMs = 60_000;        // TTL OPEN
    long halfOpenPermits = 1;           // пробный запрос (AtomicBoolean)
    CircuitKey key;                     // "atlas:stream", "atlas:suggest", "llm" (АНП-97)
}
public final class CircuitRegistry {
    CircuitBreaker get(CircuitKey key);   // Lazy: на каждый source+endpoint
}
```

Правила из доков, обязательные к реализации:
- ключ — `source:endpoint` (АНП-97);
- 5 ошибок за 30-сек окно → OPEN (скользящее окно);
- OPEN: запрос не стартует, результат `SKIPPED`, fallback на кэш `stale=true`;
- HALF_OPEN: 1 пробный запрос под `AtomicBoolean`;
- **retry не выдувает порог**: 1 пользовательский запрос = 1 ошибка, даже если внутри был retry (АНП-98);
- **приоритет** `circuit > enabled` (АНП-115); включение сброса: `enabled=true` не сбрасывает OPEN автоматически (кроме случая АНП-102: если OPEN был вызван флагом);
- TTL-форс: max 5 мин в OPEN, потом принудительный HALF_OPEN (АНП-96).

## 11. Кэш и сессии

`CacheManager`: Caffeine-компоненты (добавить зависимость) или `ConcurrentHashMap + TTL` — на выбор, контракт:
```java
public final class CachedResult<T> {
    T value; Instant cachedAt; boolean stale; SourceKey key;
}
```
- ключ кэша: `sourceId + endpoint + параметры-нормализованные`;
- TTL: справочники 1 день, результаты 5 мин (АНП-95);
- при OPEN — отдаём `stale=true`.

`SessionStore`: `ConcurrentHashMap<String, Session>` (или Redis, если появится зависимость):
```java
public final class Session {
    String id; SearchIntent intent; List<RefineStep> steps; Instant createdAt; Instant lastAccessAt;
}
```
- `Refiner` **всегда мержит** в `intent` (главное правило №1);
- атомарность: `synchronized` на session при refine; конфликт `RefineRequest.baseSessionId` с другой веткой → возврат `SESSION_EXPIRED`/CLARIFICATION.

## 12. Очередь

`RequestQueue` — bounded `ArrayBlockingQueue<RequestTask>` (лимит из `QueueProperties`, default 100).
- приоритет (`priority` в `SearchRequest`): LLM/интерактив — high; фоновое обновление кэша — low; aging — возрастание приоритета со временем ожидания;
- backpressure: при заполнении `queue.reject=true` → `QUEUE_REJECTED` 503 (АНП-109);
- дедупликация в очереди по `requestId` (повторный запрос сливается);
- consumer pool: 4–8 потоков, каждый берет `RequestTask` → `SearchOrchestrator.process`.

## 13. Метрики, алерты, health, логи

- `MetricsCollector` — инкрементальные счётчики с тегами:
  - `source.errors{source, code}` — на каждую ошибку источника;
  - `source.latency{source}_ms{histogram}`; `source.skipped{source}`; `source.circuit{source}=open`;
  - `parse.errors{source}`; `all.sources.disabled`; `queue.length`, `queue.rejected`;
  - `session.active`, `llm.errors{code}`.
- `AlertEvaluator` — локальные пороги (без Prometheus, чтобы не добавлять зависимость): `parse.errors > 10/мин` → лог WARN + webhook-заглушка; 404/редирект домена → alert «обновить Constants»; SSL → alert «проверить сертификат»; капча → alert «анти-бот».
- Health: `ready` проверяет queue не переполнена и не «все источники отключены»; `live` — только JVM.
- Логи: MDC `requestId`, `source`, `code`, `status`; сообщение = тип исключения + URL + HTTP-код + partial/timeout-флаги.

## 14. Контракты целостности

1. **Идемпотентность**: `requestId` уникален на (пользователь, короткое окно); повторный `requestId` возвращает тот же `SearchResponse` или `sessionId`.
2. **Атомарность сессии**: refine-ветка применяется только к своему `sessionId`; параллельные refine к одной сессии сериализуются.
3. **Завершённость параллельности**: `ParallelExecutor` собирает результат **по deadline**; нет состояния «зависший запрос к источнику» — все задачи либо completed, либо cancelled; SSE-потоки закрываются принудительно по флагу отключения (АНП-104).
4. **Валидность оффера**: ни один `Offer` с `null` в обязательных полях (domain, from, to, departure, price) не попадает в ответ — режет `OfferNormalizer`.
5. **Дедупликация** single-pass: на входе (парсер) по `rideId`, на выходе (Combiner) по нормализованному `offerId`.
6. **Валютная целостность**: цена сравнивается только в BYN; если валюта неизвестна — оффер помечается `attributes.currencyKnown=false` и не участвует в сортировке по цене.
7. **Данные не теряются при partial**: `partial=true` идёт до пользователя вместе с собранным, а не превращается в пустоту.

## 15. Порядок реализации (фазы)

Каждая фаза завершается «зелёными» тестами. Существующие тесты (`AtlasClientIntegrationTest`, парсер-тесты и т.д.) не ломаем.

| Фаза | Делаем | Выход |
|---|---|---|
| **0. Каркас** | пулы (scheduler, virtual threads), `RequestIdFilter`, `GlobalExceptionHandler`, `ErrorCode`, гербарий `ErrorResponse` | web-бейз, health, тест ошибок |
| **1. Исключения** | новая иерархия поверх существующих `*Client/ServiceException`, `ParseException`, карта маппинга | unit-тесты карты, advise-тесты |
| **2. Unified-модель + Normalizer** | `Offer`, `Price`, `GeoPoint`, мапперы с 5 парсеров | unit-тесты нормализации (копейки, валюты, null, диапазоны) |
| **3. Гейтвеи** | 5 адаптеров поверх существующих клиентов | unit (mock клиента) + интеграция по живому источнику |
| **4. Circuit + Registry** | breaker, registry, Scheduler | unit-тесты переходов, АНП-89…100 |
| **5. ParallelExecutor + Collector** | fan-out/in, deadline, отмена, порядок по приоритету | unit-тесты таймаутов/отмены/частичных результатов |
| **6. Tools** | `SearchBusesTool`, `SearchTrainsTool`, `SearchEventsTool`, `SearchHotelsTool` (+ ToolResult) | тесты: «один источник лежит — второй отдаёт» |
| **7. Combiner + Ranker** | дедуп, комбо, бюджет, сортировка, «отель рядом» | unit-тесты склейки (1–4 домена) |
| **8. LLM-гейт** | `LlmGateway` (contract), `RuleBasedFallback`, `Extractor/Validator/Refiner/Summarizer` | тесты извлечения интента, фолбэка, АНП-100 |
| **9. Оркестрация + Web** | `SearchOrchestrator`, `AgentController`, `SourcesAdminController`, JSON-контракты | интеграционные тесты API (happy+unhappy) |
| **10. Очередь, сессии, кэш** | `RequestQueue`, `SessionStore`, `CacheManager` (+ housekeepers) | тесты TTL, backpressure, дедупа, сессий |
| **11. Метрики, алерты, логи** | `MetricsCollector`, `AlertEvaluator`, MDC-логи | проверка метрик на каждой ошибке каталога |
| **12. Е2Е** | сквозные сценарии по happy/unhappy, каталог 1–235 | отчёт покрытия каталога |

## 16. Как развернуть в план на 1000+ шагов

Для **каждой единицы U** из фаз 0–12 раскрываются одинаковые десятки шагов. Шаблон единицы:

1. Спецификация: сигнатуры, JSON, поля, границы — `U.spec.md` (из этого документа).
2. Файлы: создать пустые каркасы (интерфейс + заглушка), подключить в Spring (bean-граф).
3. Реализация happy-пути `U` (по выбранному объёму).
4. Обработка каждого unhappy-кода из каталога 1–235, относящегося к `U` (исключение → `ErrorCode` → сообщение → метрика → alert).
5. Конфиги: `application.properties` (все новые `QueueProperties`, `CircuitPolicy`, `SessionProperties`, `LlmProperties`).
6. Юнит-тесты: happy, каждый happy-вариант, каждое исключение, таймауты, null, дедуп.
7. Интеграционные тесты (mock OkHttp `MockWebServer` — добавить в тесты; live-интеграции уже есть).
8. Тесты контрактов API (JSON фикстуры) + тесты сообщений (i18n).
9. Метрика/алерт для каждой ошибки; MDC-логи.
10. Документирование в `docs/`; обновление README-карты.
11. Кросс-проверка: не конфликтует с фазами (приоритеты, deadlock'и, cancel).
12. Ревью-чеклист + обновление списка известных ограничений.

Пример декомпозиции одной единицы (фрагмент, чтобы таргетировать гранулярность ~30–50 шагов на единицу):
- `AtlasGateway`: MarshalRequest → вызов `AtlasClient.search` → обработка 1…15 HTTP-кодов → `PartialResult` из `AtlasSearchResult.partial` → SSE: события progress/rides/done/error/unknown → нормализация `AtlasRide` → дедуп по `rideId` → маппинг ошибок в `PrincipalResult` → юнит-тесты (mock client) → интеграционный тест (живой) → метрики → alert «смена формата» → контракт-тест JSON.

Итого: 13 фаз × ~10 единиц × ~30–50 шагов ≈ **3000+ строка-шагов**; минимум 1000 достигается уже фазами 1–7.

Условие выхода плана: каждый unhappy-пункт каталога 1–235 имеет ссылку «правило → класс → тест».