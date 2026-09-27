package com.workspace.storm.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "storm.web")
public class WebProperties {

    private int maxOffers = 50;
    private String[] langs = {"ru", "en"};

}