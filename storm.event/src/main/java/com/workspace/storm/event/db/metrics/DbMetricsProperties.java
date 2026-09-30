package com.workspace.storm.event.db.metrics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Пороги эксплуатации БД (ADR-039).
 *
 * <p>Prometheus в проекте нет (ADR-VL-06), поэтому пороги живут здесь, а
 * нарушение попадает в лог и в {@code /api/v1/diagnostics/db}.
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.db.metrics")
public class DbMetricsProperties {

    /** Выключает замер altogether: аспект начинает пропускать вызовы. */
    private boolean enabled = true;

    /** Выше этого времени вызов попадает в выборку медленных запросов. */
    private long slowQueryMs = 100;

    /** Бюджет одного вызова репозитория: превышение - предупреждение в лог. */
    private long queryBudgetMs = 200;

    /** Больше ошибок БД в минуту - предупреждение уровня ERROR. */
    private int errorAlertPerMinute = 5;

    /** Сколько медленных вызовов держим в кольце для диагностики. */
    private int slowSampleSize = 50;

    /** Потоков, ожидающих соединение, при котором предупреждаем о пуле. */
    private int poolWarnThreads = 1;

    /**
     * Как часто пул проверяется в фоне, мс.
     *
     * <p>Проверка идёт по таймеру, а не из диагностики: иначе предупреждение
     * о переполнении пула появлялось бы только если кто-то открыл
     * {@code /api/v1/diagnostics/db}, то есть в бою молча не сработало бы
     * именно тогда, когда пул исчерпан.
     */
    private long poolCheckIntervalMs = 30_000L;
}