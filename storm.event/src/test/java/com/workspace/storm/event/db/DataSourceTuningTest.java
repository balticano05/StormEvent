package com.workspace.storm.event.db;

import com.workspace.storm.event.db.support.PostgresTestSupport;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("db")
class DataSourceTuningTest extends PostgresTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    void batchRewriteIsOnEvenWhenUrlIsOverriddenByEnvironment() {
        assertEquals("true", driverProperties().getProperty("rewriteBatchedStatements"),
                "иначе batch insert идёт по одной строке в сеть");
    }

    @Test
    void preparedStatementCacheIsConfigured() {
        Properties properties = driverProperties();

        assertEquals("256", properties.getProperty("prepared_statement_cache_size"));
        assertEquals("force", properties.getProperty("prepared_statement_cache_mode"));
    }

    private Properties driverProperties() {
        return ((HikariDataSource) dataSource).getDataSourceProperties();
    }
}
