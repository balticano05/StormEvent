# Метрики

Счётчики в памяти (`MetricsCollector`), без Prometheus/actuator (ADR-VL-06).

| Метрика | Теги | Тип | Источник |
|---|---|---|---|
| `http.requests` | `path`, `status` | Counter | HTTP-фильтр |
| `db.query.duration_ms` | `source` | Histogram | `DbQueryMetricsAspect` |
| `db.statements.count` | `repo` | Counter | БД |
| `db.connections.active` | — | Gauge | `DbPoolStats` |
| `source.result` | `source`, `status` | Counter | `SourceMetrics` |
| `source.latency_ms` | `source` | Counter | `SourceMetrics` |
| `source.skipped` | `source` | Counter | `SourceExecutor` |
| `offer.normalized` | `source` | Counter | `OfferMetrics` |
| `offer.dropped` | `reason` | Counter | `OfferMetrics` |
| `tool.duration` | `tool` | Counter | инструменты |
| `tool.offers` | `tool` | Counter | инструменты |
| `session.active` | — | Gauge | `SessionStore` |
| `cache.hit` / `cache.miss` / `cache.stale` | — | Counter | `CacheManager` |
| `errors` | `code`, `source` | Counter | advice |

Бакеты гистограмм: 0–100 мс, 100–500 мс, 500–1000 мс, >1000 мс.
