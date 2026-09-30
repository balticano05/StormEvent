package com.workspace.storm.event.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.stereotype.Component;

@Component
public class DatasourceCredentialsGuard implements InitializingBean {

    private static final String PASSWORD_PROPERTY = "spring.datasource.password";
    private static final String UNRESOLVED_PLACEHOLDER = "${";
    private static final String MISSING_PASSWORD = "DB_PASSWORD is not set: create .env in the repository root"
            + " with DB_URL, DB_USER and DB_PASSWORD, or export them in the environment";

    private final PropertySourcesPropertyResolver resolver;

    public DatasourceCredentialsGuard(ConfigurableEnvironment environment) {
        this.resolver = new PropertySourcesPropertyResolver(environment.getPropertySources());
        this.resolver.setIgnoreUnresolvableNestedPlaceholders(true);
    }

    @Override
    public void afterPropertiesSet() {
        verify();
    }

    void verify() {
        String password = resolver.getProperty(PASSWORD_PROPERTY, "");
        if (password.isBlank() || password.startsWith(UNRESOLVED_PLACEHOLDER)) {
            throw new IllegalStateException(MISSING_PASSWORD);
        }
    }
}