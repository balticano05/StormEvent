package com.workspace.storm.event.infra;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Инфраструктурные файлы проверяются как текст: compose не поднимается на
 * каждом прогоне, но его дрейф ловится сразу (шаги 316-320).
 */
class InfraComposeTest {

    private static final Path COMPOSE = Path.of("..", "infra", "compose.yaml");
    private static final String CONTENT = read(COMPOSE);

    @Test
    void imageIsPinnedToMajorVersion() {
        assertTrue(CONTENT.contains("image: postgres:16.15-alpine"), "образ зафиксирован тегом");
        assertFalse(CONTENT.contains(":latest"), "latest в проде запрещён");
    }

    @Test
    void passwordComesFromEnvFileOnly() {
        assertTrue(CONTENT.contains("env_file:"), "переменные из env/.env");
        assertTrue(CONTENT.contains("POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?"),
                "пустой пароль валит запуск, а не создаёт БД без пароля");
        assertFalse(CONTENT.contains("POSTGRES_PASSWORD: postgres"),
                "дефолтный пароль в репозитории отсутствует");
    }

    @Test
    void statsStatementsIsPreloaded() {
        assertTrue(CONTENT.contains("shared_preload_libraries=pg_stat_statements"),
                "расширение должно быть в разделяемой памяти, иначе выборки запросов не работают");
    }

    @Test
    void slowQueriesAndDeadlocksAreLogged() {
        assertTrue(CONTENT.contains("log_min_duration_statement="), "медленные запросы в лог");
        assertTrue(CONTENT.contains("log_lock_waits=on"), "ожидания блокировок в лог");
        assertTrue(CONTENT.contains("deadlock_timeout=1s"), "тупики ловим быстро");
    }

    @Test
    void portIsBoundToLoopbackOnly() {
        assertTrue(CONTENT.contains("\"127.0.0.1:${POSTGRES_PORT"), "БД не должна слушать все интерфейсы");
    }

    @Test
    void serviceHasHealthcheckLimitsAndVolume() {
        assertTrue(CONTENT.contains("healthcheck:"), "healthcheck обязателен");
        assertTrue(CONTENT.contains("pg_isready"), "проверка живости - настоящий запрос");
        assertTrue(CONTENT.contains("restart: unless-stopped"));
        assertTrue(CONTENT.contains("deploy:"), "лимиты ресурсов обязательны");
        assertTrue(CONTENT.contains("memory: 1g"));
        assertTrue(CONTENT.contains("cpus: \"2.0\""));
        assertTrue(CONTENT.contains("storm-pgdata:/var/lib/postgresql/data"), "данные на томе");
    }

    @Test
    void initScriptsAreMountedReadOnly() {
        assertTrue(CONTENT.contains("./initdb:/docker-entrypoint-initdb.d:ro"));
    }

    @Test
    void logRotationIsBounded() {
        assertTrue(CONTENT.contains("max-size: \"10m\""));
        assertTrue(CONTENT.contains("max-file: \"3\""));
    }

    private static String read(Path path) {
        for (Path candidate : List.of(path, Path.of("infra").resolve(path.getFileName()))) {
            try {
                return Files.readString(candidate);
            } catch (Exception ignored) {
                continue;
            }
        }
        throw new IllegalStateException("не прочитан " + path.toAbsolutePath());
    }
}