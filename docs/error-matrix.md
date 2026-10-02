# Матрица «ошибка → источник → класс → код → тест»

Сводная карта покрытия каталога ошибок (шаги 888–893, приложение B).

| # ошибки | Источник | Фаза | Класс | ErrorCode | Тест | Статус |
|---|---|---|---|---|---|---|
| 1–15 | Общие HTTP | 5,7 | ClientException / Gateway | SOURCE_UNAVAILABLE и др. | AtlasGatewayTest и др. | долг |
| 16–36 | Сетевые | 5,7 | ClientException(timeout) | SOURCE_TIMEOUT | BzdGatewayTest (таймаут) | долг |
| 37–70 | Контент/парсинг | 6 | OfferNormalizer / ParseException | PARSE_ERROR | OfferNormalizerTest, DateParserTest | done |
| 71–86 | Atlas | 6,7 | AtlasSseParser → Gateway | — | AtlasOfferMapperTest | долг |
| 87–120 | BZD | 6,7 | BzdGateway + warmup | SOURCE_ERROR | BzdRouteParserTest | долг |
| 121–150 | TicketBus | 6,7 | TicketBusGateway | — | TicketBusRaceParserTest | долг |
| 151–170 | TicketPro | 6,7 | TicketProGateway | — | TicketProEventParserTest | долг |
| 171–200 | BelHotel | 6,7 | BelHotelGateway | — | BelHotelClientIntegrationTest | долг |
| 201–220 | Юридические | 13,17 | SourcesAdmin | — | DiagnosticsAccess tests | долг |
| 221–235 | РБ/РФ локали | 6,13 | OfferNormalizer / Local | — | DateParserTest | долг |

Правило: каждая строка в конце фаз должна иметь `class+test`; «долг» закрывается
в фазе 17 при e2e-прогоне с живыми API.
