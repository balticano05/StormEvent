package com.workspace.storm.event.web.health;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public boolean isHealthy() {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}