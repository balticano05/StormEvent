# E2E отчёт

Запуск: 2026-10-02, ветка `dev`.

| Сценарий | Статус | Комментарий |
|---|---|---|
| HP-1 автобус happy (скелет) | done | `E2eSmokeTest.hp1BusHappySkeleton` |
| HP-2 поезд happy (скелет) | done | `E2eSmokeTest.hp2TrainHappySkeleton` |
| HP-16 бюджет времени | done | `responseRespectsDeadline` |
| unhappy LLM → fallback | done (skeleton) | stub-путь проверен |
| Все источники отключены → сообщение | долг | до фазы 17 live |
| Сессия протухла → SESSION_EXPIRED | долг | housekeeper тест есть |
| Load baseline 50 RPS | отложено | шаг 914 |

Живые интеграции (Atlas/BZD/TicketBus/TicketPro/BelHotel) прогоняются тегами
`integration` и из CI исключены — нужен доступ к сети/ключам.
