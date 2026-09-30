package com.workspace.storm.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Параметры эксплуатации БД: готовность, retention и партиции. */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.db")
public class DbProperties {

    /**
     * Сколько миграций должно быть применено, чтобы приложение считалось
     * готовым принимать трафик (шаг 369). Несовпадение - ready=false и запись
     * уровня ERROR: приложение поднялось на старой схеме.
     */
    private int expectedMigrations = 4;

    /**
     * Сколько дней живут записи журнала запросов (ADR-033).
     *
     * <p>Срок свой у каждого журнала, а не общий: у {@code request_log} 30
     * дней, у {@code source_error_log} — 90. Одно число на оба вычистило бы
     * историю ошибок на два месяца раньше, чем обещает документация.
     */
    private int requestLogRetentionDays = 30;

    /** Сколько дней живут записи журнала ошибок источников (ADR-033). */
    private int sourceErrorLogRetentionDays = 90;

    /**
     * Сколько дней живут агрегаты по часам (ADR-033).
     *
     * <p>Три месяца, а не 30 дней: по этим строкам считаются сравнения
     * «вчера против недели назад», и месяц истории для этого не хватает.
     */
    private int statsRetentionDays = 90;

    /**
     * Включено ли фоновое обслуживание: создание партиций и ретеншн
     * (шаги 311, 352-353).
     *
     * <p>Выключается в тестах: контейнер Postgres общий на весь прогон, и
     * партиции, созданные фоновой задачей при старте контекста, меняют число
     * таблиц и планы запросов у соседних тестов.
     */
    private boolean maintenanceEnabled = true;

    /** Размер порции в ограниченных DELETE чистки (шаг 347). */
    private int cleanupBatch = 2000;

    /** На сколько месяцев вперёд держатся готовые партиции журналов. */
    private int partitionMonthsAhead = 2;

    /**
     * Минимальная глубина хранения партиций в месяцах, независимо от
     * ретеншна строк.
     *
     * <p>Партиция удаляется, только когда выполнены оба условия: её месяц
     * целиком старше ретеншна <em>и</em> месяцев назад не меньше
     * {@code partitionMinKeepMonths}. Второе условие — страховка от
     * неверной настройки срока: при {@code request-log-retention-days=1}
     * лог не должен лишиться данных за вчера.
     */
    private int partitionMinKeepMonths = 1;

    /**
     * Сколько миллисекунд живёт кэш среза состояния миграций.
     *
     * <p>Readiness опрашивают каждые несколько секунд, а срез стоит два
     * SQL-запроса: без кэша диагностика сама нагружает базу, о готовности
     * которой спрашивает, а при рассинхроне забивает журнал одинаковыми
     * ERROR-строками.
     */
    private long migrationsCacheTtlMs = 5_000L;

    /**
     * Срок хранения конкретной таблицы в днях.
     *
     * <p>Неизвестная таблица получает срок {@code request_log}, а не ноль:
     * молчаливое отсутствие настройки должно означать «чистим по умолчанию»,
     * а не «храним вечно».
     */
    public int retentionDaysFor(String table) {
        return switch (table) {
            case "request_log" -> requestLogRetentionDays;
            case "source_error_log" -> sourceErrorLogRetentionDays;
            case "stats_source_hourly" -> statsRetentionDays;
            default -> requestLogRetentionDays;
        };
    }
}
