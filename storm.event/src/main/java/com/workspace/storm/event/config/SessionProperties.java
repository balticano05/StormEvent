package com.workspace.storm.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.session")
public class SessionProperties {

    private int ttlMinutes = 15;

    public int ttlSeconds() {
        return ttlMinutes * 60;
    }
}
