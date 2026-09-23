# Ошибки 1–36: Общие HTTP и сетевые проблемы

Каждая ошибка ловится на своём уровне: `IOException → *ClientException → *ServiceException → Tool → пустой список`.
Метрика на каждую ошибку: `source.errors{source="atlas", type="500"}`.

## Общие HTTP-ошибки (1–15)

### Ошибка 1. 500 Internal Server Error
Atlas/BZD/TicketBus вернул 500. Клиент кидает `*ClientException`. Service → `*ServiceException`.
Tool ловит, логирует, возвращает пустой список. Пользователь видит остальные источники.

### Ошибка 2. 502 Bad Gateway
Прокси источника лёг. То же поведение. В логах — `BadGatewayException`.

### Ошибка 3. 503 Service Unavailable
Источник на обслуживании. Может прислать `Retry-After`. Клиент сохраняет значение, Tool ставит источник на паузу на N секунд.

### Ошибка 4. 504 Gateway Timeout
Источник долго отвечает, прокси отвалился. Tool возвращает пустой с флагом `timeout=true`.

### Ошибка 5. 429 Too Many Requests
Источник заблокировал за частоту. Клиент читает `Retry-After`, ставит источник на паузу.
Если пауза >5 сек — Tool возвращает пустой, пользователь видит «источник временно недоступен».

### Ошибка 6. 403 Forbidden
IP заблокирован, или anti-bot. Tool логирует, ставит источник в circuit OPEN.

### Ошибка 7. 401 Unauthorized
Источник требует токен, которого нет. Если это cookie-сессия (BZD) — warmup не сработал.
Retry warmup, потом circuit OPEN.

### Ошибка 8. 404 Not Found
URL устарел (Constants не обновили). Все запросы к источнику падают. Tool возвращает пустой.
Alert в мониторинг — нужно обновить Constants.

### Ошибка 9. 301/302 редирект на другой домен
Atlas переехал с atlasbus.by на atlasbus.com. OkHttp следует редиректу, но cookie-jar для старого домена.
Сессия теряется. **Fix:** обновить Constants + cookie-jar по новому домену.

### Ошибка 10. 307/308 редирект с сохранением метода
POST на `/api/search` редиректится на `/api/v2/search`. Тело сохраняется. OkHttp следует.
Если клиент не настроен — теряет тело.

### Ошибка 11. Бесконечный редирект
A → B → A → B. OkHttp `followRedirects(20)` останавливается, кидает `ProtocolException`.
Tool ловит, возвращает пустой.

### Ошибка 12. Редирект на HTTPS с HTTP
Источник переехал на HTTPS. OkHttp следует. Если сертификат самоподписанный — `SSLHandshakeException`.

### Ошибка 13. 204 No Content
Источник вернул пустой ответ. Парсер кидает `ParseException: empty body`. Tool возвращает пустой.

### Ошибка 14. 200 OK, но тело пустое
Same as 204. Парсер не находит элементов.

### Ошибка 15. 200 OK, но тело — HTML-страница ошибки
Источник вернул «Произошла ошибка» с кодом 200. Парсер не находит ожидаемых селекторов.
`ParseException: 0 rides found`.

## Сетевые ошибки (16–36)

### Ошибка 16. DNS не резолвится
atlasbus.by не резолвится. OkHttp кидает `UnknownHostException`. Tool возвращает пустой.
Если долго — circuit OPEN.

### Ошибка 17. Connection refused
Источник закрыл порт. `ConnectException`. Tool возвращает пустой.

### Ошибка 18. Connection reset
Источник разорвал соединение на середине. `SocketException: Connection reset`. Retry 1 раз. Если снова — пустой.

### Ошибка 19. Read timeout
Источник не отвечает 10 сек. `SocketTimeoutException`. Tool возвращает пустой с `timeout=true`.

### Ошибка 20. Write timeout
Запрос большой, источник не принимает. `SocketTimeoutException` на write. Для SSE-запросов с большим body.

### Ошибка 21. Connect timeout
TCP handshake не прошёл за 5 сек. `ConnectTimeoutException`.

### Ошибка 22. SSL handshake failed
Источник сменил сертификат на самоподписанный. `SSLHandshakeException`. Tool возвращает пустой.
Alert — нужно проверить сертификат.

### Ошибка 23. SSL certificate expired
Сертификат источника истёк. `SSLHandshakeException`. Все запросы падают. Circuit OPEN.
Пользователь: «Источник недоступен».

### Ошибка 24. TLS version mismatch
Источник требует TLS 1.3, клиент предлагает 1.2. `SSLHandshakeException`.
**Fix:** обновить OkHttp/JDK.

### Ошибка 25. IPv6 не работает
Источник резолвится в IPv6, но сеть не поддерживает. `ConnectException`. OkHttp пробует IPv4 fallback. Если нет — пустой.

### Ошибка 26. MTU проблемы
Большие пакеты теряются. Соединение устанавливается, но данные не идут. Timeout.

### Ошибка 27. Прокси-сервер упал
Если OkHttp настроен через прокси. `ProxyConnectionException`.

### Ошибка 28. Обрыв SSE-потока
Atlas SSE оборвался на середине (без done). Парсер видит отсутствие done.
Возвращает `partial=true` с тем, что успел.

### Ошибка 29. SSE-поток завис
Atlas открыл поток, но не присылает события. Read timeout 10 сек.
Парсер прерывает, возвращает `partial=true`.

### Ошибка 30. SSE-поток слишком долгий
Atlas шлёт 1000 событий progress. Поток не закрывается 60 сек. Max duration 30 сек.
Клиент принудительно закрывает, возвращает `partial=true`.

### Ошибка 31. SSE-событие с невалидным JSON
Atlas прислал `data: {broken json}`. Парсер кидает `JsonProcessingException`.
Событие пропускается, остальные парсятся.

### Ошибка 32. SSE-событие неизвестного типа
Atlas прислал `event: newEventType`. Парсер игнорирует неизвестные события, логирует.

### Ошибка 33. SSE-событие error от Atlas
Atlas прислал `event: error, data: {"message": "..."}`. Парсер сохраняет ошибку в `AtlasSearchResult.errors`.
Tool решает: если rides уже есть — вернуть их, если нет — пустой.

### Ошибка 34. SSE-событие done без rides
Atlas прислал done, но rides не было. Легитимный пустой результат. Tool возвращает пустой список.

### Ошибка 35. SSE-событие пришло вне порядка
done пришёл до rides. Парсер буферизует, обрабатывает по мере поступления. Если done без rides — пустой.

### Ошибка 36. SSE-событие с дубликатом
Atlas прислал один и тот же ride дважды. Парсер дедуплицирует по `rideId`.