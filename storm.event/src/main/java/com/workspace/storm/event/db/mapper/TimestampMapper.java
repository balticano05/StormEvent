package com.workspace.storm.event.db.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public final class TimestampMapper {

    private TimestampMapper() {}

    public static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    public static Instant toInstant(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return toInstant(ts);
    }
}