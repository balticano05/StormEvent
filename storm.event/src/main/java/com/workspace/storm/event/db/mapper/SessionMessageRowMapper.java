package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.SessionMessageEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SessionMessageRowMapper implements RowMapper<SessionMessageEntity> {

    @Override
    public SessionMessageEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        SessionMessageEntity e = new SessionMessageEntity();
        e.setId(rs.getLong("id"));
        e.setSessionId(UUID.fromString(rs.getString("session_id")));
        e.setRole(rs.getString("role"));
        e.setKind(rs.getString("kind"));
        e.setText(rs.getString("text"));
        String reqId = rs.getString("request_id");
        e.setRequestId(reqId != null ? UUID.fromString(reqId) : null);
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        return e;
    }
}