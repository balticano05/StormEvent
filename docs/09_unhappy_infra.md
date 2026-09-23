# Анхэппи пути 39–45: Инфраструктура и конфигурация

OkHttp, виртуальные потоки, Jackson, docker-compose, мониторинг, Constants.

### АНП-39. OkHttp pool исчерпан
5 соединений заняты, шестой запрос ждёт в очереди. По таймауту — `ConnectionPoolTimeoutException`.
Tool логирует, возвращает пустой.

### АНП-40. Виртуальные потоки не включены
`spring.threads.virtual.enabled=false`. Параллельные вызовы идут на платформенных потоках.
Всё работает, но медленнее. Лог-warning при старте.

### АНП-41. Jackson 3 vs Jackson 2 аннотации
`AtlasRide` импортирует `com.fasterxml.jackson.annotation.JsonProperty` (Jackson 2), но парсер
использует `tools.jackson`. Поле `carrier_phones` молча не мапится → `null`.
**Guard:** после парсинга валидатор проверяет обязательные поля → логирует missing.
**Fix:** перевести импорты на `tools.jackson.annotation`.

### АНП-42. Docker-compose поднял Postgres, но JPA нет в pom
Приложение стартует, но DataSource не создаётся — Spring не ругается, потому что нет
`spring-boot-starter-data-jpa`. Просто лишний контейнер. Warning в логах не появится.
**Fix:** либо добавить JPA, либо убрать Postgres из compose.

### АНП-43. Prometheus скребёт /actuator/prometheus, которого нет
Нет `spring-boot-starter-actuator` и `micrometer-registry-prometheus`. Prometheus видит 404.
**Fix:** добавить зависимости.

### АНП-44. AtlasClient читает SSE через body.string()
Большой поток (100+ рейсов) грузится целиком в память перед парсингом.
Для 500 рейсов — OOM или 10+ сек.
**Fix:** стримить через `ResponseBody.source()` и парсить построчно.

### АНП-45. Constants устарели
Atlas изменил URL с `/api/search/stream` на `/api/v2/search/stream`.
Все запросы возвращают 404. Tool возвращает пустой.
**Fix:** обновить Constants, добавить тест на актуальность.