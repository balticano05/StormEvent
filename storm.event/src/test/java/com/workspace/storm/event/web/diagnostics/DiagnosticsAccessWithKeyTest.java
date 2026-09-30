package com.workspace.storm.event.web.diagnostics;

import com.workspace.storm.event.db.support.DbConnectionTestSupport;
import com.workspace.storm.event.web.filter.AdminKeyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * С заданным ключом служебный отчёт отдаётся только с верным заголовком
 * (ADR-VL-10, шаги 304, 330).
 */
@Tag("db")
@SpringBootTest
@TestPropertySource(properties = "storm.admin.apiKey=test-admin-key")
class DiagnosticsAccessWithKeyTest extends DbConnectionTestSupport {

    private static final String KEY = "test-admin-key";

    @Autowired
    private DiagnosticsController diagnostics;

    @Autowired
    private AdminKeyFilter adminKeyFilter;

    private MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.standaloneSetup(diagnostics).addFilters(adminKeyFilter).build();
    }

    @Test
    void databaseReportNeedsValidKey() throws Exception {
        mvc.perform(get("/api/v1/diagnostics/db"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diagnostics/db").header("X-Api-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseReportReturnsCountersWithValidKey() throws Exception {
        mvc.perform(get("/api/v1/diagnostics/db").header("X-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['db.pool'].poolStatsAvailable").value(true))
                .andExpect(jsonPath("$['db.pool'].maximum").value(6))
                .andExpect(jsonPath("$['db.queries']['db.statements.count']").isNumber())
                .andExpect(jsonPath("$['db.alerts']['db.query.budget.ms']").isNumber())
                .andExpect(jsonPath("$['db.migrations'].ready").value(true))
                .andExpect(jsonPath("$['db.migrations'].applied").value(4))
                .andExpect(jsonPath("$['db.migrations'].expected").value(4))
                .andExpect(jsonPath("$['db.catalog']['db.tables.count']").value(11));
    }

    @Test
    void slowQueriesWorkWithoutPgStatStatementsExtension() throws Exception {
        mvc.perform(get("/api/v1/diagnostics/slow-queries").header("X-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['pg_stat_statements.available']").value(false))
                .andExpect(jsonPath("$['pg_stat_statements.byTotalTime']").isEmpty())
                .andExpect(jsonPath("$['application.slowStatements']").isArray());
    }
}