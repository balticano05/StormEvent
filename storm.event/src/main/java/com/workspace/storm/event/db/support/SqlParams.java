package com.workspace.storm.event.db.support;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class SqlParams {

    private final MapSqlParameterSource source = new MapSqlParameterSource();

    private SqlParams() {
    }

    public static SqlParams create() {
        return new SqlParams();
    }

    public SqlParams add(String name, Object value) {
        source.addValue(name, value);
        return this;
    }

    public SqlParams addInstant(String name, Instant value) {
        if (value == null) {
            source.addValue(name, null, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            source.addValue(name, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
        }
        return this;
    }

    public MapSqlParameterSource build() {
        return source;
    }
}
