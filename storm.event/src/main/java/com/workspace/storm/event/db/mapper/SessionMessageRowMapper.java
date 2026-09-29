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
        e.setSessionId(rs.getObject("session_id", UUID.class));
        e.setRole(rs.getString("role"));
        e.setKind(rs.getString("kind"));
        e.setText(rs.getString("text"));
        e.setRequestId(rs.getObject("request_id", UUID.class));
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        return e;
    }
}