package com.workspace.storm.event;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

}