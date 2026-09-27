package com.workspace.storm.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.timeout")
public class TimeoutProperties {

    private long softBudgetMs = 40000;
    private long hardCeilingMs = 60000;
    private long minSourceCallMs = 1000;

}