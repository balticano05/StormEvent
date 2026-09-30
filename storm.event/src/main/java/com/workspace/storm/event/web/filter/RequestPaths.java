package com.workspace.storm.event.web.filter;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Путь запроса внутри приложения, независимо от маппинга DispatcherServlet.
 *
 * <p>{@code getServletPath()} для контроллеров на маппинге {@code /} пустой, а
 * {@code getPathInfo()} — {@code null}: и то, и другое годится только для
 * {@code web.xml}-конфигурации. Поэтому путь собирается из
 * {@code requestURI} за вычетом {@code contextPath}, и одинаково работает за
 * развёртыванием в context path.
 */
final class RequestPaths {

    private RequestPaths() {
    }

    /** Путь запроса без context path, всегда с ведущим слэшем. */
    static String withinApplication(HttpServletRequest request) {
        String uri = Optional.ofNullable(request.getRequestURI()).orElse("");
        String contextPath = Optional.ofNullable(request.getContextPath()).orElse("");
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return path.isEmpty() ? "/" : path;
    }
}