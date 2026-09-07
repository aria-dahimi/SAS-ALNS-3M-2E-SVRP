package testsupport;

import config.ConfigurationLoader;
import config.ProjectSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/** Test helpers for loading a complete valid configuration. */
public final class TestConfigSupport {

    private static final String BASE_CONFIG_RESOURCE = "/config/base-test.properties";

    private TestConfigSupport() {
    }

    public static Properties baseProperties() {
        Properties properties = new Properties();
        try (InputStream input = TestConfigSupport.class.getResourceAsStream(BASE_CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource " + BASE_CONFIG_RESOURCE);
            }
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load base test configuration.", exception);
        }
    }

    public static ProjectSettings applyBaseConfiguration() {
        return applyConfiguration(Map.of());
    }

    public static ProjectSettings applyConfiguration(Map<String, String> overrides) {
        Properties properties = baseProperties();
        overrides.forEach((key, value) -> {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        });
        return apply(properties);
    }

    public static ProjectSettings apply(Properties properties) {
        Path temporaryConfig = writeTemporaryConfig(properties);
        try {
            return ConfigurationLoader.loadAndApply(temporaryConfig);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot apply temporary test configuration.", exception);
        } finally {
            try {
                Files.deleteIfExists(temporaryConfig);
            } catch (IOException ignored) {
                // Cleanup errors must not hide the test result.
            }
        }
    }

    public static Path writeTemporaryConfig(Properties properties) {
        try {
            Path temporaryConfig = Files.createTempFile("sas-alns-test-", ".properties");
            try (OutputStream output = Files.newOutputStream(temporaryConfig)) {
                properties.store(output, "sas-alns automated test configuration");
            }
            return temporaryConfig;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create temporary test configuration.", exception);
        }
    }
}
