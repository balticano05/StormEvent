package com.workspace.storm.event.db.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TimestampMapper {

    private TimestampMapper() {}

    public static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    public static Instant toInstant(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return toInstant(ts);
    }

    public static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    public static Instant toUtc(Timestamp ts) {
        if (ts == null) return null;
        return ts.toInstant().atOffset(ZoneOffset.UTC).toInstant();
    }
}