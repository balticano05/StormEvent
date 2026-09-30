package com.workspace.storm.event.db.repository;

import com.workspace.storm.event.db.entity.StatsSourceHourlyEntity;
import com.workspace.storm.event.db.mapper.StatsSourceHourlyRowMapper;
import com.workspace.storm.event.db.support.SqlParams;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StatsRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final StatsSourceHourlyRowMapper rowMapper = new StatsSourceHourlyRowMapper();

    public void incrementHourly(Instant hour, String source, String code) {
        String sql = """
            INSERT INTO storm.stats_source_hourly (hour, source, code, count)
            VALUES (:hour, :source, :code, 1)
            ON CONFLICT (hour, source, code) DO UPDATE SET count = stats_source_hourly.count + 1
            """;
        jdbc.update(sql, SqlParams.create()
                .addInstant("hour", hour)
                .add("source", source)
                .add("code", code)
                .build());
    }

    public List<StatsSourceHourlyEntity> selectTopErrors(Instant since, int limit) {
        String sql = """
            SELECT hour, source, code, SUM(count) as count
            FROM storm.stats_source_hourly
            WHERE hour >= :since
            GROUP BY hour, source, code
            ORDER BY count DESC
            LIMIT :limit
            """;
        return jdbc.query(sql, SqlParams.create()
                .addInstant("since", since)
                .add("limit", limit)
                .build(), rowMapper);
    }
}
