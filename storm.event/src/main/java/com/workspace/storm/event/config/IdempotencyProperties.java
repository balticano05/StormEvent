package com.workspace.storm.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.idempotency")
public class IdempotencyProperties {

    private int ttlMinutes = 5;

    public int ttlSeconds() {
        return ttlMinutes * 60;
    }
}
