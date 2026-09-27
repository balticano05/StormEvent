package com.workspace.storm.event.config;

import com.workspace.storm.event.OkHttpProperties;
import com.workspace.storm.event.web.filter.RequestIdFilter;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    @Bean
    public OkHttpClient okHttpClient(OkHttpProperties props) {
        return new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(props.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
                .callTimeout(props.getCallTimeoutMs(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(props.isRetryOnConnectionFailure())
                .connectionPool(new ConnectionPool(
                        props.getMaxIdleConnections(),
                        props.getKeepAliveMinutes(),
                        TimeUnit.MINUTES))
                .build();
    }

    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}