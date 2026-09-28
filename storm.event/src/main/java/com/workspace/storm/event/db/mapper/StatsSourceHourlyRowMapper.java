package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.StatsSourceHourlyEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StatsSourceHourlyRowMapper implements RowMapper<StatsSourceHourlyEntity> {

    @Override
    public StatsSourceHourlyEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        StatsSourceHourlyEntity e = new StatsSourceHourlyEntity();
        e.setHour(TimestampMapper.toInstant(rs.getTimestamp("hour")));
        e.setSource(rs.getString("source"));
        e.setCode(rs.getString("code"));
        e.setCount(rs.getInt("count"));
        return e;
    }
}