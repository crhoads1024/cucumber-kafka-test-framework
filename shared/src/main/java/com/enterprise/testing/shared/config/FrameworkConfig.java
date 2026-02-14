package com.enterprise.testing.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration for the test framework.
 * Loads from framework.properties and allows environment variable overrides.
 * 
 * Convention: environment variables override properties file values.
 * ENV_VAR naming: uppercase, dots replaced with underscores.
 * e.g., "app.base.url" -> APP_BASE_URL
 */
public class FrameworkConfig {

    private static final Properties props = new Properties();
    private static FrameworkConfig instance;

    private FrameworkConfig() {
        loadProperties();
    }

    public static synchronized FrameworkConfig getInstance() {
        if (instance == null) {
            instance = new FrameworkConfig();
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("framework.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load framework.properties", e);
        }
    }

    /**
     * Get a config value. Checks environment variables first (with dot-to-underscore mapping),
     * then falls back to properties file, then to the provided default.
     */
    public String get(String key, String defaultValue) {
        // Check env var first (app.base.url -> APP_BASE_URL)
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        // Then system property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }
        // Then properties file
        return props.getProperty(key, defaultValue);
    }

    public String get(String key) {
        return get(key, null);
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    // --- Convenience accessors for common config ---

    public String getBaseUrl() {
        return get("app.base.url", "http://localhost:8080");
    }

    public String getKafkaBootstrapServers() {
        return get("kafka.bootstrap.servers", "localhost:9092");
    }

    public String getDbUrl() {
        return get("db.url", "jdbc:postgresql://localhost:5432/testdb");
    }

    public String getDbUsername() {
        return get("db.username", "testuser");
    }

    public String getDbPassword() {
        return get("db.password", "testpass");
    }

    public String getDataOutputDir() {
        return get("data.output.dir", "./generated-data");
    }

    public boolean useTestcontainers() {
        return getBoolean("use.testcontainers", true);
    }
}
