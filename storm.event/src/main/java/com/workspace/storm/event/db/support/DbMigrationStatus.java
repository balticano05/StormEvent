package com.workspace.storm.event.db.support;

import com.workspace.storm.event.config.DbProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Состояние миграций Flyway для readiness (шаги 369-370).
 *
 * <p>Приложение, поднятое на старой схеме, не готово принимать трафик: запрос
 * к несуществующей колонке упал бы уже на пользователе. Поэтому /ready
 * сравнивает число применённых миграций с ожидаемым и при расхождении пишет
 * ERROR в лог.
 *
 * <p>Срез кэшируется на {@code storm.db.migrations.cache-ttl-ms} (по умолчанию
 * 5 с): балансировщик дёргает /ready каждые несколько секунд, а два SQL-запроса
 * и ERROR-строка на каждый проб — это шум в журнале эксплуатации БД и лишняя
 * нагрузка на ту самую базу, о готовности которой спрашивают. Логируется не
 * каждый рассинхрон, а только его появление и исчезновение.
 */
@Component
@RequiredArgsConstructor
public class DbMigrationStatus {

    private static final Logger log = LoggerFactory.getLogger(DbMigrationStatus.class);

    /**
     * Считаем только версионные миграции: Flyway добавляет служебную запись о
     * создании схемы с пустой версией, и в счётчик она попадать не должна.
     */
    private static final String APPLIED_QUERY = """
            SELECT count(*) FROM storm.flyway_schema_history
            WHERE success AND version IS NOT NULL
            """;

    private static final String VERSION_QUERY = """
            SELECT version FROM storm.flyway_schema_history
            WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1
            """;

    /** TTL кэша среза: короче интервала пробов, но не на каждом запросе. */
    private static final long DEFAULT_CACHE_TTL_MS = 5_000L;

    private final JdbcTemplate jdbc;
    private final DbProperties properties;

    private final Object lock = new Object();
    private Cached cached = Cached.empty();

    /** Текущий срез состояния миграций, из кэша. */
    public MigrationSnapshot snapshot() {
        Cached current = cached;
        if (current.isFresh()) {
            return current.snapshot();
        }
        synchronized (lock) {
            if (cached.isFresh()) {
                return cached.snapshot();
            }
            MigrationSnapshot fresh = query();
            logStateChange(cached.snapshot(), fresh);
            cached = new Cached(fresh, Instant.now().plusMillis(cacheTtlMs()));
            return fresh;
        }
    }

    private MigrationSnapshot query() {
        Integer applied = jdbc.queryForObject(APPLIED_QUERY, Integer.class);
        String version = jdbc.queryForObject(VERSION_QUERY, String.class);
        int expected = properties.getExpectedMigrations();
        int count = Optional.ofNullable(applied).orElse(0);
        return new MigrationSnapshot(count, Optional.ofNullable(version).orElse(""), expected, count >= expected);
    }

    /**
     * Пишет в лог только смену состояния, а не каждый рассинхрон.
     *
     * <p>Срез обновляется каждые пять секунд, и балансировщик дёргает /ready
     * не меньше: WARN на каждый проб превратился бы в сотни строк в час на
     * инстанс. Состояние сравнивается с предыдущим срезом, поэтому при
     * ровном «применено больше ожидаемого» строка выйдет один раз.
     *
     * <p>Отдельно про {@code >=} вместо {@code ==}: база с миграцией V5 и
     * бинарь, ожидающий четыре, отвечает «не готов» и не встаёт в
     * балансировщик, хотя его схема совместима. Лишние миграции — не повод
     * не обслуживать трафик, это WARN, а не ERROR.
     */
    private void logStateChange(MigrationSnapshot previous, MigrationSnapshot current) {
        if (previous.ahead() == current.ahead() && previous.ready() == current.ready()) {
            return;
        }
        if (current.ahead()) {
            log.warn("db.migrations.ahead: применено {} при ожидаемых {} (версия {}) — "
                            + "похоже на откат приложения на старшую версию схемы",
                    current.applied(), current.expected(), current.version());
        } else if (current.ready()) {
            log.info("db.migrations.ready: применено {} из {} (версия {})",
                    current.applied(), current.expected(), current.version());
        } else {
            log.error("db.migrations.notApplied: применено {} из {} ожидаемых (версия {})",
                    current.applied(), current.expected(), current.version());
        }
    }

    private long cacheTtlMs() {
        return properties.getMigrationsCacheTtlMs() > 0
                ? properties.getMigrationsCacheTtlMs()
                : DEFAULT_CACHE_TTL_MS;
    }

    /**
     * Срез состояния миграций.
     *
     * <p>{@code ahead} — применено строго больше ожидаемого, то есть база
     * новее бинаря. Флаг отделён от {@code ready}, потому что это разные
     * инциденты с разной реакцией: «не готов» блокирует трафик, «впереди»
     * только предупреждает.
     */
    public record MigrationSnapshot(int applied, String version, int expected, boolean ready) {

        public boolean ahead() {
            return applied > expected;
        }
    }

    private record Cached(MigrationSnapshot snapshot, Instant validUntil) {
        static Cached empty() {
            return new Cached(new MigrationSnapshot(0, "", 0, false), Instant.EPOCH);
        }

        boolean isFresh() {
            return Instant.now().isBefore(validUntil);
        }
    }
}