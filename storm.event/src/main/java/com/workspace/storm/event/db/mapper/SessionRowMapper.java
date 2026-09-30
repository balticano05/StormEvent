package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.SessionEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SessionRowMapper implements RowMapper<SessionEntity> {

    @Override
    public SessionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        SessionEntity e = new SessionEntity();
        e.setId(rs.getObject("id", UUID.class));
        e.setIntent(rs.getString("intent"));
        e.setLastAccessAt(TimestampMapper.toInstant(rs.getTimestamp("last_access_at")));
        e.setExpiresAt(TimestampMapper.toInstant(rs.getTimestamp("expires_at")));
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        e.setTtlSeconds(rs.getInt("ttl_seconds"));
        e.setState(rs.getString("state"));
        return e;
    }
}