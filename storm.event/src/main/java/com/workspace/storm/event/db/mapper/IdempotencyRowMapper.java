package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.IdempotencyEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IdempotencyRowMapper implements RowMapper<IdempotencyEntity> {

    @Override
    public IdempotencyEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        IdempotencyEntity e = new IdempotencyEntity();
        e.setRequestId(rs.getObject("request_id", UUID.class));
        e.setResponseJson(rs.getString("response_json"));
        e.setSessionId(rs.getObject("session_id", UUID.class));
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        e.setExpiresAt(TimestampMapper.toInstant(rs.getTimestamp("expires_at")));
        return e;
    }
}