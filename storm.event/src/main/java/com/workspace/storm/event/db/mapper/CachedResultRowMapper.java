package com.workspace.storm.event.db.mapper;

import com.workspace.storm.event.db.entity.CachedResultEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CachedResultRowMapper implements RowMapper<CachedResultEntity> {

    @Override
    public CachedResultEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        CachedResultEntity e = new CachedResultEntity();
        e.setCacheKey(rs.getString("cache_key"));
        e.setSource(rs.getString("source"));
        e.setDomain(rs.getString("domain"));
        e.setPayloadJson(rs.getString("payload_json"));
        e.setCreatedAt(TimestampMapper.toInstant(rs.getTimestamp("created_at")));
        e.setExpiresAt(TimestampMapper.toInstant(rs.getTimestamp("expires_at")));
        e.setStale(rs.getBoolean("stale"));
        return e;
    }
}