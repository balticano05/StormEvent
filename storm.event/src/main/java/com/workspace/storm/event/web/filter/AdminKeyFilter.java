package com.workspace.storm.event.web.filter;

import com.workspace.storm.event.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Закрывает служебный API заголовком {@code X-Api-Key} (ADR-VL-10).
 *
 * <p>Диагностика БД отдаёт счётчики, планы запросов и статистику пула: это
 * не для публичного доступа. Пока ключ не задан, служебные пути отвечают 503 -
 * «выключено» честнее, чем молча отдавать данные всем.
 */
public class AdminKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String PROTECTED_PREFIX = "/api/v1/diagnostics/";
    private static final String DISABLED_BODY = "{\"status\":\"ADMIN_API_DISABLED\"}";
    private static final String DENIED_BODY = "{\"status\":\"UNAUTHORIZED\"}";

    private final AdminProperties properties;

    public AdminKeyFilter(AdminProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String expected = properties.getApiKey();
        if (!StringUtils.hasText(expected)) {
            respond(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, DISABLED_BODY);
            return;
        }
        if (!isAuthorized(request, expected)) {
            respond(response, HttpServletResponse.SC_UNAUTHORIZED, DENIED_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Путь берём из URI, а не из {@code getServletPath()}: контроллеры висят на
     * DispatcherServlet с маппингом {@code /}, поэтому servletPath пустой и по
     * нему фильтр не защищал бы ничего.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RequestPaths.withinApplication(request).startsWith(PROTECTED_PREFIX);
    }

    private boolean isAuthorized(HttpServletRequest request, String expected) {
        return Optional.ofNullable(request.getHeader(API_KEY_HEADER))
                .filter(StringUtils::hasText)
                .map(value -> MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        value.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    private void respond(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}