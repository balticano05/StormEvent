package com.workspace.storm.event.db.mapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {}

    public static String toJson(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    public static JsonNode toJsonNode(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON", e);
        }
    }

    public static <T> T fromJsonNode(JsonNode node, Class<T> clazz) {
        if (node == null || node.isNull()) return null;
        try {
            return MAPPER.treeToValue(node, clazz);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to convert JsonNode", e);
        }
    }
}