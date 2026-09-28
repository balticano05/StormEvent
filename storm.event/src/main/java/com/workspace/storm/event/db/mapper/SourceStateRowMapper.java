package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.SourceStateEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SourceStateRowMapper implements RowMapper<SourceStateEntity> {

    @Override
    public SourceStateEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        SourceStateEntity e = new SourceStateEntity();
        e.setSource(rs.getString("source"));
        e.setEnabled(rs.getBoolean("enabled"));
        e.setDraining(rs.getBoolean("draining"));
        e.setRampUntil(TimestampMapper.toInstant(rs.getTimestamp("ramp_until")));
        e.setUpdatedAt(TimestampMapper.toInstant(rs.getTimestamp("updated_at")));
        return e;
    }
}