package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.SourceErrorLogEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SourceErrorLogRowMapper implements RowMapper<SourceErrorLogEntity> {

    @Override
    public SourceErrorLogEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        SourceErrorLogEntity e = new SourceErrorLogEntity();
        e.setId(rs.getLong("id"));
        e.setRequestId(UUID.fromString(rs.getString("request_id")));
        e.setSource(rs.getString("source"));
        e.setCode(rs.getString("code"));
        e.setMessage(rs.getString("message"));
        e.setLatencyMs((Integer) rs.getObject("latency_ms"));
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        return e;
    }
}