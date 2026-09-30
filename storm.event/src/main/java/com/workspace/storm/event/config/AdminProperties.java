package com.workspace.storm.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Ключ служебного API (ADR-VL-10). */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.admin")
public class AdminProperties {

    /** Приходит в заголовке {@code X-Api-Key}. Пусто - служебный API выключен. */
    private String apiKey = "";
}
