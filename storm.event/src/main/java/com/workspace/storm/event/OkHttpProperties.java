package com.workspace.storm.event;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "okhttp")
public class OkHttpProperties {

    private long connectTimeoutMs = 5_000;
    private long readTimeoutMs = 10_000;
    private long writeTimeoutMs = 10_000;
    private long callTimeoutMs = 0;
    private boolean retryOnConnectionFailure = true;
    private int maxIdleConnections = 5;
    private long keepAliveMinutes = 5;

}