package com.workspace.storm.event.web.filter;

import com.workspace.storm.event.config.AdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Служебные счётчики не должны отдаваться без ключа (ADR-VL-10).
 */
class AdminKeyFilterTest {

    private static final String SECRET = "s3cr3t-key";
    private static final String PROTECTED = "/api/v1/diagnostics/db";

    @Test
    void rejectsRequestWithoutKey() throws Exception {
        MockHttpServletResponse response = invoke(properties(SECRET), request(PROTECTED), new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void rejectsRequestWithWrongKey() throws Exception {
        MockHttpServletRequest request = request(PROTECTED);
        request.addHeader("X-Api-Key", "wrong");

        MockHttpServletResponse response = invoke(properties(SECRET), request, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsRequestWithEmptyKeyHeader() throws Exception {
        MockHttpServletRequest request = request(PROTECTED);
        request.addHeader("X-Api-Key", "");

        assertEquals(401, invoke(properties(SECRET), request, new MockFilterChain()).getStatus());
    }

    @Test
    void passesRequestWithValidKey() throws Exception {
        MockHttpServletRequest request = request(PROTECTED);
        request.addHeader("X-Api-Key", SECRET);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = invoke(properties(SECRET), request, chain);

        assertEquals(200, response.getStatus());
        assertEquals(PROTECTED, requestUri(chain), "запрос дошёл до контроллера");
    }

    @Test
    void disabledApiAnswersServiceUnavailableInsteadOfOpenAccess() throws Exception {
        MockHttpServletResponse response = invoke(properties(""), request(PROTECTED), new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("ADMIN_API_DISABLED"));
    }

    @Test
    void blankApiKeyCountsAsDisabled() throws Exception {
        assertEquals(503, invoke(properties("   "), request(PROTECTED), new MockFilterChain()).getStatus());
    }

    @Test
    void protectedPathIsDetectedEvenWhenServletPathIsEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED);
        request.setServletPath("");
        request.addHeader("X-Api-Key", "wrong");

        assertEquals(401, invoke(properties(SECRET), request, new MockFilterChain()).getStatus(),
                "маппинг DispatcherServlet на / оставляет servletPath пустым - фильтр обязан сработать");
    }

    @Test
    void protectedPathIsDetectedBehindContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/storm" + PROTECTED);
        request.setContextPath("/storm");
        request.setServletPath(PROTECTED);

        assertEquals(401, invoke(properties(SECRET), request, new MockFilterChain()).getStatus(),
                "приложение за context path защищено так же");
    }

    @Test
    void publicEndpointsAreNotFiltered() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = invoke(properties(SECRET), request("/api/v1/health/live"), chain);

        assertEquals(200, response.getStatus());
        assertEquals("/api/v1/health/live", requestUri(chain));
    }

    @Test
    void publicEndpointSurvivesDisabledAdminApi() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertEquals(200, invoke(properties(""), request("/api/v1/health/live"), chain).getStatus());
    }

    private String requestUri(MockFilterChain chain) {
        return ((MockHttpServletRequest) chain.getRequest()).getRequestURI();
    }

    private MockHttpServletResponse invoke(AdminProperties properties, MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AdminKeyFilter(properties).doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath("");
        request.setRequestURI(path);
        return request;
    }

    private AdminProperties properties(String apiKey) {
        AdminProperties properties = new AdminProperties();
        properties.setApiKey(apiKey);
        return properties;
    }
}