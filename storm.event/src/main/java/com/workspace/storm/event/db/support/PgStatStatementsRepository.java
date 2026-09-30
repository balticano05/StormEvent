package com.workspace.storm.event.db.support;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Топ запросов по времени из {@code pg_stat_statements} (шаг 304).
 *
 * <p>Расширение подключается в {@code infra/compose.yaml}
 * ({@code shared_preload_libraries}) и в {@code infra/initdb/10-init-db.sql}.
 * Если расширения нет - запрос не падает, а возвращает пустой результат с
 * пометкой недоступности: диагностика обязана работать и на стенде без
 * расширения.
 */
@Repository
@RequiredArgsConstructor
public class PgStatStatementsRepository {

    private static final String TOP_SQL = """
            SELECT queryid::text AS queryid,
                   left(query, 300) AS query,
                   calls,
                   total_exec_time,
                   mean_exec_time,
                   rows
            FROM pg_stat_statements
            WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
            ORDER BY total_exec_time DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public Optional<List<StatementStat>> topByTotalTime(int limit) {
        try {
            return Optional.of(jdbc.query(TOP_SQL,
                    (rs, rowNum) -> new StatementStat(
                            rs.getString("queryid"),
                            rs.getString("query"),
                            rs.getLong("calls"),
                            rs.getDouble("total_exec_time"),
                            rs.getDouble("mean_exec_time"),
                            rs.getLong("rows")),
                    limit));
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        try {
            return jdbc.queryForObject("SELECT count(*) FROM pg_stat_statements", Long.class) != null;
        } catch (DataAccessException e) {
            return false;
        }
    }

    /** Срез статистики по одному запросу. */
    public record StatementStat(
            String queryId,
            String query,
            long calls,
            double totalExecMs,
            double meanExecMs,
            long rows) {
    }
}
