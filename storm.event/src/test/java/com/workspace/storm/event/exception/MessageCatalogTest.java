package com.workspace.storm.event.exception;

import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MessageCatalogTest {

    private Properties load(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " не найден");
            String content = StreamUtils.copyToString(in, StandardCharsets.ISO_8859_1);
            props.load(new java.io.StringReader(new String(content.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)));
        }
        return props;
    }

    @Test
    void everyErrorCodeHasRuAndEnMessage() throws IOException {
        Properties ru = load("messages.properties");
        Properties en = load("messages_en.properties");
        for (ErrorCode code : ErrorCode.values()) {
            String key = code.getI18nKey();
            assertTrue(ru.containsKey(key), "ru missing: " + key);
            assertTrue(en.containsKey(key), "en missing: " + key);
        }
    }

    @Test
    void ruAndEnKeySetsMatch() throws IOException {
        Properties ru = load("messages.properties");
        Properties en = load("messages_en.properties");
        assertEquals(ru.keySet(), en.keySet());
    }
}
