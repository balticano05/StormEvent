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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Пока ключ не задан, служебный API закрыт целиком, а публичные хендлеры
 * продолжают работать (ADR-VL-10).
 *
 * <p>Ключ пустую��ся явно: локальный {@code .env} импортируется в тесты через
 * {@code spring.config.import}, и без переопределения проверка зависела бы от
 * секретов разработчика.
 */
@Tag("db")
@SpringBootTest
@TestPropertySource(properties = "storm.admin.apiKey=")
class DiagnosticsAccessDisabledTest extends DbConnectionTestSupport {

    private static final String LIVE = "/api/v1/health/live";
    private static final String READY = "/api/v1/health/ready";

    @Autowired
    private DiagnosticsController diagnostics;

    @Autowired
    private AdminKeyFilter adminKeyFilter;

    @Autowired
    private com.workspace.storm.event.web.health.HealthController health;

    private MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.standaloneSetup(diagnostics, health)
                .addFilters(adminKeyFilter)
                .build();
    }

    @Test
    void diagnosticsDatabaseIsDisabledWithoutKey() throws Exception {
        mvc.perform(get("/api/v1/diagnostics/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"ADMIN_API_DISABLED\"}", true));
    }

    @Test
    void diagnosticsSlowQueriesIsDisabledWithoutKey() throws Exception {
        mvc.perform(get("/api/v1/diagnostics/slow-queries"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void publicHealthEndpointsStayOpen() throws Exception {
        mvc.perform(get(LIVE)).andExpect(status().isOk());
        mvc.perform(get(READY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }
}