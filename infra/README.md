# Инфраструктура StormEvent

Docker Compose для PostgreSQL 16 и развёртывание одной ноды (ADR-045).
Схема базы не здесь — её создаёт Flyway из `storm.event/src/main/resources/db/migration`.

## Состав

| Файл | Назначение |
|---|---|
| `compose.yaml` | PostgreSQL 16: тюнинг сервера, healthcheck, volume, лимиты |
| `initdb/10-init-db.sql` | Идемпотентный скрипт первого старта: расширения и схема |
| `env/.env.example` | Шаблон переменных, реальный `.env` в git не попадает |

## Запуск

```bash
cp infra/env/.env.example infra/env/.env
# заполнить POSTGRES_PASSWORD
docker compose --env-file infra/env/.env -f infra/compose.yaml up -d
docker compose --env-file infra/env/.env -f infra/compose.yaml ps
```

`healthy` в `ps` означает, что `pg_isready` прошёл — с этого момента можно
запускать приложение.

Приложение читает пароль из `secrets.properties` в корне репозитория:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/storm?sslmode=disable
spring.datasource.username=storm
spring.datasource.password=<тот же пароль, что в infra/env/.env>
```

`sslmode` приходит из URL и намеренно не зашит в `application.properties`: на
локальной машине это `disable`, на боевом хосте `require` (или `verify-full` с
корневым сертификатом) — менять нужно одно место, а не код. Локальный compose
слушает только `127.0.0.1`, поэтому наружу БД не выставлена.

## Что настроено и почему

| Параметр | Значение | Причина |
|---|---|---|
| `shared_preload_libraries` | `pg_stat_statements` | `/api/v1/diagnostics/slow-queries` читает эту статистику |
| `checkpoint_timeout` | `15min` | Длинный чекпоинт на одной ноде — длинная пауза обслуживания |
| `log_min_duration_statement` | `100` | Медленный запрос попадает в лог контейнера |
| `log_lock_waits`, `deadlock_timeout` | `on`, `1s` | Дедлоки видны в логе, а не только в счётчиках |
| `default_statistics_target` | `100` | При 45k строк в партиции планировщик ошибался в оценке в 5000 раз |
| `shared_buffers` / `effective_cache_size` | `256MB` / `768MB` | Бюджет ноды 1 GB из `deploy.resources` |

Настройки автовакуума по конкретным таблицам живут в миграции
`V4__hot_query_indexes.sql`: это свойство таблицы, а не кластера, и оно должно
одинаково применяться в тестах.

## Бэкап

Дамп снимается снаружи контейнера, в каталог хоста:

```bash
docker compose --env-file infra/env/.env -f infra/compose.yaml exec -T postgres \
  pg_dump -U storm -d storm --format=custom > backup/storm-$(date +%F).dump
```

Расписание — через cron на хосте (в MVP без отдельного сервиса):

```
17 3 * * * cd /srv/storm && mkdir -p backup && docker compose --env-file infra/env/.env -f infra/compose.yaml exec -T postgres pg_dump -U storm -d storm --format=custom > backup/storm-$(date +\%F).dump
```

Восстановление:

```bash
docker compose --env-file infra/env/.env -f infra/compose.yaml exec -T postgres \
  pg_restore -U storm -d storm --clean --if-exists < backup/storm-2026-09-30.dump
```

Retention дампов приложение не делает: ротация 7/4/1 — зона cron.

## Обслуживание

```bash
# состояние
docker compose --env-file infra/env/.env -f infra/compose.yaml ps
docker compose --env-file infra/env/.env -f infra/compose.yaml logs --tail=100 postgres

# перезапуск после смены compose.yaml
docker compose --env-file infra/env/.env -f infra/compose.yaml up -d

# остановить, данные сохранить
docker compose --env-file infra/env/.env -f infra/compose.yaml down

# снести вместе с данными (осторожно)
docker compose --env-file infra/env/.env -f infra/compose.yaml down -v
```

`initdb/10-init-db.sql` выполняется только при пустом `PGDATA`. Чтобы применить
его к существующему кластеру, выполните файл вручную — он идемпотентный:

```bash
docker compose --env-file infra/env/.env -f infra/compose.yaml exec -T postgres \
  psql -U storm -d storm < infra/initdb/10-init-db.sql
```