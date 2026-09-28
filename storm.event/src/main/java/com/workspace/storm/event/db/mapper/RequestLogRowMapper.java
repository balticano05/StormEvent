package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.RequestLogEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class RequestLogRowMapper implements RowMapper<RequestLogEntity> {

    @Override
    public RequestLogEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        RequestLogEntity e = new RequestLogEntity();
        e.setId(rs.getLong("id"));
        e.setRequestId(UUID.fromString(rs.getString("request_id")));
        String sessionId = rs.getString("session_id");
        e.setSessionId(sessionId != null ? UUID.fromString(sessionId) : null);
        e.setText(rs.getString("text"));
        e.setIntentJson(rs.getString("intent_json"));
        e.setStatus(rs.getString("status"));
        e.setDurationMs((Integer) rs.getObject("duration_ms"));
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        return e;
    }
}