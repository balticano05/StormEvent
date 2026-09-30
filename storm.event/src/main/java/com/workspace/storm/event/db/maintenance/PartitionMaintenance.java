package com.workspace.storm.event.db.maintenance;

import com.workspace.storm.event.config.DbProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Месячные партиции журналов: создаются заранее и уходят целиком
 * (ADR-033, ADR-044).
 *
 * <p>Retention журнала - {@code DROP PARTITION}, а не {@code DELETE}: удаление
 * месячного куска мгновенно и не пишет в WAL, тогда как ограниченный
 * {@code DELETE} остаётся добором для старых данных, попавших в
 * {@code _default}.
 *
 * <p>Партиция создаётся заранее ({@code storm.db.partition-months-ahead}), иначе
 * запись в несуществующий месяц ушла бы в {@code _default} и осталась там
 * навсегда. Если строки этого месяца уже лежат в {@code _default}, создание
 * партиции PostgreSQL отклонит - это ожидаемо и не ошибка: сообщаем в лог и
 * работаем дальше на {@code _default}.
 */
@Component
@RequiredArgsConstructor
public class PartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenance.class);

    /** Таблицы с временным ключом партиционирования. */
    private static final List<Partitioned> TABLES = List.of(
            new Partitioned("request_log", "created_at"),
            new Partitioned("source_error_log", "created_at"),
            new Partitioned("stats_source_hourly", "hour"));

    /** Дней в месяце для перевода срока хранения в месяцы. */
    private static final int DAYS_IN_MONTH = 30;

    private static final Pattern MONTHLY_NAME = Pattern.compile(
            "^(?<table>[a-z_]+)_(?<year>\\d{4})_(?<month>\\d{2})$");

    private static final String LIST_SQL = """
            SELECT c.relname
            FROM pg_class c
            JOIN pg_inherits i ON i.inhrelid = c.oid
            JOIN pg_class p ON p.oid = i.inhparent
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'storm' AND p.relname = ?
            ORDER BY c.relname
            """;

    private final JdbcTemplate jdbc;
    private final DbProperties properties;

    /**
     * Создаёт партиции на {@code months} месяцев вперёд от {@code from}.
     *
     * <p>Список партиций читается один раз на таблицу, а не на каждый месяц:
     * при двух месяцах вперёд это 3 запроса к каталогу вместо 9, а при
     * месячных прогонах накапливается заметная просадка на пустом кластере.
     */
    public List<String> ensureUpcoming(YearMonth from, int months) {
        List<String> created = new ArrayList<>();
        for (Partitioned table : TABLES) {
            List<String> existing = listPartitions(table.name());
            for (int offset = 0; offset <= months; offset++) {
                YearMonth month = from.plusMonths(offset);
                if (!existing.contains(partitionName(table.name(), month))) {
                    created.addAll(create(table.name(), month));
                }
            }
        }
        return created;
    }

    /** Создаёт партиции вперёд от текущего месяца на {@code partition-months-ahead}. */
    public List<String> ensureUpcoming() {
        return ensureUpcoming(YearMonth.now(ZoneOffset.UTC), properties.getPartitionMonthsAhead());
    }

    /**
     * Удаляет месячные партиции старше {@code keepMonths}, игнорируя ретеншн
     * строк.
     *
     * <p>Служебный метод для тестов: удалить всё старше заданного месяца
     * нужно, чтобы не ждать месяц реального времени. В приложении партиции
     * удаляются по {@link #dropOlderThanRetention(YearMonth)}.
     *
     * <p>Удаляются только партиции с именем {@code <table>_YYYY_MM}: DEFAULT
     * и любое другое имя остаются нетронутыми - терять таблицу целиком из-за
     * опечатки в дате нельзя.
     */
    List<String> dropOlderThan(YearMonth keepFrom, int keepMonths) {
        YearMonth oldest = keepFrom.minusMonths(Math.max(0, keepMonths));
        List<String> dropped = new ArrayList<>();
        for (Partitioned table : TABLES) {
            for (String name : listPartitions(table.name())) {
                monthOf(name).filter(month -> month.isBefore(oldest))
                        .ifPresent(month -> drop(table.name(), name, dropped));
            }
        }
        return dropped;
    }

    /**
     * Удаляет старые партиции по ретеншну каждой таблицы.
     *
     * <p>Месяц удаляется, когда ушли оба условия: месяц целиком старше
     * ретеншна этой таблицы <em>и</em> прошло не меньше
     * {@code partition-min-keep-months}. Порция в месяцах — это
     * {@code ceil(days / 30)} с округлением вверх: при 31 дне получается
     * 2 месяца, а не 1, то есть лишние данные, но не потерянные.
     *
     * <p>Границы партиций считаются по UTC: {@code created_at} хранится в
     * UTC, а отображаемое время проекта — Europe/Minsk (ADR-016). Смена зоны
     * сдвинула бы границы месяцев на несколько часов и часть строк попадала
     * бы не в тот месяц.
     */
    public List<String> dropOlderThanRetention() {
        return dropOlderThanRetention(YearMonth.now(ZoneOffset.UTC));
    }

    /** Удаляет старые партиции относительно заданного месяца. */
    public List<String> dropOlderThanRetention(YearMonth currentMonth) {
        List<String> dropped = new ArrayList<>();
        for (Partitioned table : TABLES) {
            int retentionDays = properties.retentionDaysFor(table.name());
            int months = retentionMonths(retentionDays);
            int boundary = Math.max(months, properties.getPartitionMinKeepMonths());
            dropped.addAll(dropTablePartitionsOlderThan(table.name(), currentMonth, boundary));
        }
        return dropped;
    }

    /** Срок в днях, округлённый вверх до целых месяцев. */
    static int retentionMonths(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        return (int) Math.ceil(retentionDays / (double) DAYS_IN_MONTH);
    }

    private List<String> dropTablePartitionsOlderThan(String table, YearMonth currentMonth, int keepMonths) {
        YearMonth oldest = currentMonth.minusMonths(keepMonths);
        List<String> dropped = new ArrayList<>();
        for (String name : listPartitions(table)) {
            monthOf(name)
                    .filter(month -> month.isBefore(oldest))
                    .ifPresent(month -> drop(table, name, dropped));
        }
        return dropped;
    }

    /** Имена листовых партиций таблицы, включая {@code _default}. */
    public List<String> listPartitions(String table) {
        return jdbc.queryForList(LIST_SQL, String.class, table);
    }

    private List<String> create(String tableName, YearMonth month) {
        String name = partitionName(tableName, month);
        String from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        String to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        String sql = "CREATE TABLE IF NOT EXISTS storm." + name
                + " PARTITION OF storm." + tableName
                + " FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
        try {
            jdbc.execute(sql);
        } catch (RuntimeException e) {
            log.warn("Не создал партицию storm.{}: {} (записи этого месяца останутся в _default)",
                    name, e.getMessage());
            return List.of();
        }
        applyVacuumSettings(name);
        log.info("Создал партицию storm.{} до {}", name, month);
        return List.of(name);
    }

    private void drop(String table, String name, List<String> dropped) {
        jdbc.execute("DROP TABLE IF EXISTS storm." + name);
        log.info("Удалил партицию storm.{} по retention", name);
        dropped.add(name);
    }

    /** Имя месячной партиции: {@code <table>_YYYY_MM}. */
    static String partitionName(String table, YearMonth month) {
        return table + "_" + month.getYear() + "_" + String.format(Locale.ROOT, "%02d", month.getMonthValue());
    }

    /**
     * Автовакуум на партиции - те же значения, что ставит V4 на существующие
     * партиции (scale_factor 0.02 / 0.01). На самой партиционированной
     * таблице Postgre storage-параметры запрещает.
     */
    private void applyVacuumSettings(String name) {
        try {
            jdbc.execute("ALTER TABLE storm." + name + " SET ("
                    + "autovacuum_vacuum_scale_factor = 0.02, "
                    + "autovacuum_analyze_scale_factor = 0.01)");
        } catch (RuntimeException e) {
            // Партиция уже создана: падать из-за настроек автовакуума нельзя,
            // иначе вызывающий решит, что месяца нет, и записи уйдут в _default.
            log.warn("Партиция storm.{} создана, но настройки автовакуума не применились: {}",
                    name, e.getMessage());
        }
    }

    private static Optional<YearMonth> monthOf(String partitionName) {
        Matcher matcher = MONTHLY_NAME.matcher(partitionName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.of(
                    Integer.parseInt(matcher.group("year")),
                    Integer.parseInt(matcher.group("month"))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Партиционированная таблица и её временная колонка. */
    private record Partitioned(String name, String column) {
    }
}