package com.enterprise.testing.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Centralized JSON utility. All test layers use this to ensure
 * consistent serialization/deserialization across the framework.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtil() {}

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON: " + json, e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }

    public static void writeToFile(Object obj, Path path) {
        try {
            path.toFile().getParentFile().mkdirs();
            MAPPER.writeValue(path.toFile(), obj);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to file: " + path, e);
        }
    }

    public static <T> T readFromFile(Path path, Class<T> clazz) {
        try {
            return MAPPER.readValue(path.toFile(), clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON from file: " + path, e);
        }
    }

    public static <T> T readFromFile(Path path, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(path.toFile(), typeRef);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON from file: " + path, e);
        }
    }
}
