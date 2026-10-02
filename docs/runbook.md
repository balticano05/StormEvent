# Runbook

| Проблема | Симптом | Действие |
|---|---|---|
| БД недоступна | `/ready`=503, `connection error` | поднять `infra/compose.yaml`, проверить `DB_URL/DB_PASSWORD` |
| Расхождение миграций | `/ready`=503, ERROR в логе | накатить Flyway: перезапустить приложение; проверить `MigrationCountTest` |
| Источник отвалился (403/500) | warnings в ответе чата | посмотреть `source.errors` в метриках; при необходимости `disable` через `/api/v1/sources` |
| Анти-бот | 403 без повторов, ALERT WARN | источник отключить вручную, обход не делаем (ADR-VL-11) |
| Перерасход времени запроса | 504 `REQUEST_TIMEOUT` | проверить остаток бюджета; уменьшить `storm.timeout.soft-budget-ms` не оправдано, смотреть источники |
| LLM недоступен | ответ только с `[stub]` | проверить `OPENROUTER_API_KEY`/`OPENROUTER_MODEL`; stub — fallback |
| Много ошибок парсинга | ALERT `parse.errors` | проверить селекторы парсеров; смена формата источника |
| Утечка соединений | `db.connections.active` растёт | `leakDetectionThreshold=30s` в dev; перезапустить приложение |

## Добавление нового источника

1. Клиент/парсер (`client/`, `parser/`).
2. Маппер entity → `Offer` (`normalize/source/`).
3. `SourceGateway`-адаптер в `gateway/` + bean в `GatewayConfig`.
4. Tool в `tool/` при необходимости.
5. Сид `source_state` в V2-миграции (если нужен).
6. Тесты: парсер, маппер, gateway (MockWebServer/моки).

## Добавление ErrorCode

`ErrorCode` (статус + i18n-ключ) → `messages*.properties` (ru/en) →
`MessageResolverTest`/`GlobalExceptionHandlerTest` → таблица в
`docs/error-matrix.md`.
