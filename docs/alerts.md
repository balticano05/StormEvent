# Алерты

Локальные пороги в `AlertEvaluator`, реакция — WARN/ERROR через `AlertSink` (по умолчанию `LogAlertSink`).

| Алерт | Порог | Действие |
|---|---|---|
| `parse.errors` | >10/мин | лог WARN; сигнал смены формата источника |
| `source.errors` | N/мин (конфиг) | лог WARN; ручное выключение источника |
| `db.query.errors` | >5/мин | лог ERROR |
| `db.pool.wait_ms` | >200 мс p50 | лог WARN |
| `INTERNAL_ERROR` | >0 | лог ERROR |
| `all_sources_unavailable` | все выключены | readiness=false |
| `migrations.not_applied` | расхождение | readiness=false + ERROR |
| `anti-bot` | 403/капча | лог WARN; источник отключается вручную |

Внешние вебхуки — отложены (интерфейс `AlertSink` готов к реализации).
