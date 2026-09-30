package com.workspace.storm.event.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceCredentialsGuardTest {

    @Test
    void acceptsPasswordResolvedFromEnvFile() {
        assertDoesNotThrow(() -> guard(environmentWith("resolved-secret")).verify());
    }

    @Test
    void rejectsMissingPasswordProperty() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> guard(new MockEnvironment()).verify());

        assertTrue(failure.getMessage().contains("DB_PASSWORD is not set"), failure.getMessage());
    }

    @Test
    void rejectsPasswordLeftAsUnresolvedPlaceholder() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> guard(environmentWith("${DB_PASSWORD}")).verify());

        assertTrue(failure.getMessage().contains("DB_PASSWORD is not set"), failure.getMessage());
    }

    @Test
    void rejectsBlankPassword() {
        assertThrows(IllegalStateException.class, () -> guard(environmentWith("   ")).verify());
    }

    private DatasourceCredentialsGuard guard(MockEnvironment environment) {
        return new DatasourceCredentialsGuard(environment);
    }

    private MockEnvironment environmentWith(String password) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.password", password);
        return environment;
    }
}