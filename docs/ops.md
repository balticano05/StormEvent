# Эксплуатация

## Старт

```bash
cd storm.event
mvn spring-boot:run
```

Требуется PostgreSQL 16 из `infra/compose.yaml` (порт на loopback), `.env` с
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `OPENROUTER_API_KEY` (опционально),
`OPENROUTER_MODEL`, `STORM_ADMIN_KEY`.

## Проверка здоровья

```bash
curl http://localhost:8080/api/v1/health/live
curl http://localhost:8080/api/v1/health/ready
```

`ready=false` при расхождении миграций или падении БД — смотреть
`GET /api/v1/diagnostics/db` с заголовком `X-Api-Key`.

## Диагностика

- `/api/v1/diagnostics/db` — метрики пула, миграции, slow queries.
- `/api/v1/diagnostics/slow-queries` — `pg_stat_statements` (админ-ключ).
- Логи: `logs/storm-app.log`, `logs/storm-db.log`, `logs/storm-source.log`.

## Включение/выключение источника

```bash
curl -X POST -H "X-Api-Key: $STORM_ADMIN_KEY" \
  http://localhost:8080/api/v1/sources/bzd/disable
```

`disable` ставит `enabled=false, draining=true`; новые запросы идут мимо
источника (SKIPPED), упавший источник не роняет поиск.

## Housekeeper/ретеншн

- Сессии — `SessionHousekeeper` (раз в минуту, порция 1000).
- Ретеншн-журналы — `RetentionService` + `PartitionMaintenance`
  (ночь, cron из `application.properties`).
- Повторный запуск housekeeper идемпотентен: удаление идёт порциями по `expires_at`.

## Обновление курсов валют

Курсы-затычки в `OfferNormalizer` (`RUB_TO_BYN`, `USD_TO_BYN`, `EUR_TO_BYN`) —
обновляются вручную; live-источник отложен (ADR-047).

## Бэкапы

`pg_dump` по расписанию из контейнера БД (см. `infra/README.md`); том
`storm-pgdata` persistent. Проверка восстановления — поднять compose с нуля в
тестовом окружении.
