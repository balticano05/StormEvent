# Ретеншн и чистки

Кто чем чистит, с какой частотой и в каком порядке. Значения TTL задаёт
приложение (ADR-038), база только хранит и индексирует.

## Сроки хранения

| Данные | Срок | Способ |
|---|---|---|
| `session` | 15 минут с последнего обращения | `expires_at` + `findExpiredIds`/`deleteByIds` |
| `session_message` | вместе с сессией | `ON DELETE CASCADE` |
| `cached_result` | индивидуальный `expires_at` источника | `findExpiredKeys` + `deleteByKeys` |
| `idempotency` | 5 минут | `deleteExpired(before, limit)` |
| `request_log` | 30 дней | `DROP PARTITION` прод, `deleteOlderThan` добор |
| `source_error_log` | 90 дней | `DROP PARTITION` прод, `deleteOlderThan` добор |
| `stats_source_hourly` | 3 месяца | `DROP PARTITION` прод |
| `request_log` для истёкшей сессии | не удаляется | сессия обнуляет ссылку, строка остаётся |

## Порядок прохода

1. **Сессии.** `SELECT id ... WHERE expires_at < now() ORDER BY expires_at
   LIMIT 1000` → один `DELETE ... WHERE id IN (...)`. Каскад уносит реплики
   диалога. Пачка ограничена, чтобы не держать длинную транзакцию.
2. **Кэш.** `SELECT cache_key ... WHERE expires_at < now() LIMIT 1000` →
   один `DELETE`. Просроченное, но годное отдаётся как `stale`, из базы
   уходит штатно.
3. **Дедупликация.** Один `DELETE ... WHERE request_id IN (SELECT ...
   WHERE expires_at < now() LIMIT 1000)`.
4. **Журналы.** `DELETE ... WHERE created_at < now() - interval 'N days'
   LIMIT N` — только добором; основной путь — удаление партиций.

Один проход идемпотентен: повторный ничего не удаляет и не падает на пустом
результате (проверено `RetentionCleanupTest`).

## Месячные партиции

`request_log`, `source_error_log` и `stats_source_hourly` партиционированы по
времени (ADR-033). В схеме есть только `DEFAULT`-партиции: пока фоновой задачи
нет, запись идёт туда и ничего не теряется.

```sql
-- создать партицию на месяц вперёд
CREATE TABLE storm.request_log_2026_11 PARTITION OF storm.request_log
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');

-- убрать старый месяц вместо DELETE по строкам
DROP TABLE storm.request_log_2026_08;
```

Порядок важен: партиция создаётся **до** начала месяца, `DROP PARTITION` —
**после** того, как месяц вышел за retention. `DEFAULT`-партиция остаётся
последним рубежом: если партицию не создали, запись не падает, а попадает в
`_default`, и это видно в метриках размера партиций.

## Чего чистка не делает

- Не удаляет `request_log` вместе с сессией: аудит живёт 30 дней независимо.
- Не трогает `stats_source_hourly` в пределах 3 месяцев — по ним считаются
  сравнения «вчера/неделю назад».
- Не чистит кэш «по времени жизни источника» принудительно: источник сам
  задаёт `expires_at`, максимум — `stale`.

## Проверки

- `RetentionCleanupTest` — проход удаляет только истёкшее, уважает лимиты и
  повторяем.
- `MonthBoundaryTest` — записи на стыке месяцев не теряются, retention режет
  по границе месяца, агрегаты по часам не смешиваются.
- `IndexUsageTest` — чистка идёт по индексам, а не по последовательному
  сканированию.
- `SchemaConstraintTest.everyPartitionedTableHasDefaultPartition` — партиция
  «слепого месяца» не исчезла при следующей миграции.
