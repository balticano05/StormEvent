package com.workspace.storm.event.db.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Число миграций в каталоге совпадает с ожидаемым для readiness (шаг 369).
 *
 * <p>Число миграций живёт в трёх местах: в файлах {@code V*.sql}, в
 * {@code storm.db.expected-migrations} и в {@link MigrationTest}. Добавление
 * V5 роняет {@code MigrationTest}, но если править только его, приложение в
 * бою вечно отвечает 503: применённых миграций четыре, а ожидается пять —
 * без единого падающего теста. Этот тест закрывает именно тихую
 * рассинхронизацию конфигурации.
 */
class MigrationCountTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");
    private static final Path PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final String EXPECTED_MIGRATIONS = "storm.db.expected-migrations";

    @Test
    void expectedMigrationsPropertyMatchesFilesOnDisk() {
        assertEquals(migrationFilesOnDisk().size(), configuredExpectedMigrations(),
                EXPECTED_MIGRATIONS + " разошёлся с числом файлов V*.sql в " + MIGRATIONS
                        + ": добавь миграцию и обнови application.properties");
    }

    @Test
    void migrationsOnDiskAreContiguousFromOne() {
        List<Integer> versions = migrationFilesOnDisk().stream()
                .map(MigrationCountTest::versionOf)
                .sorted()
                .toList();

        assertEquals(
                IntStream.rangeClosed(1, versions.size()).boxed().toList(),
                versions,
                "версии миграций должны идти подряд с единицы, без пропусков и дублей");
    }

    @Test
    void defaultValueMatchesFilesOnDiskToo() {
        assertEquals(migrationFilesOnDisk().size(),
                new com.workspace.storm.event.config.DbProperties().getExpectedMigrations(),
                "значение по умолчанию в DbProperties разошлось с числом миграций: "
                        + "оно попадёт в контекст, если свойство не задано");
    }

    @Test
    void everyMigrationFileHasFlywayNaming() {
        for (String name : migrationFilesOnDisk()) {
            assertTrue(name.matches("^V\\d+__[a-z0-9_]+\\.sql$"),
                    "имя миграции не разбирается Flyway: " + name);
        }
    }

    private static int versionOf(String fileName) {
        return Integer.parseInt(fileName.replaceFirst("^V", "").split("__")[0]);
    }

    private int configuredExpectedMigrations() {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(PROPERTIES)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("не прочитал " + PROPERTIES, e);
        }
        String value = properties.getProperty(EXPECTED_MIGRATIONS);
        assertTrue(value != null && !value.isBlank(),
                EXPECTED_MIGRATIONS + " не задан в " + PROPERTIES);
        return Integer.parseInt(value.trim());
    }

    private List<String> migrationFilesOnDisk() {
        if (!Files.isDirectory(MIGRATIONS)) {
            throw new UncheckedIOException(new IOException("каталог миграций не найден: " + MIGRATIONS
                    + " (тест запускается из каталога модуля storm.event)"));
        }
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}