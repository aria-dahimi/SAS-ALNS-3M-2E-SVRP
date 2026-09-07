package config;

import java.util.Locale;

/** Execution environment recorded for reproducibility. */
public enum ExecutionEnvironment {
    LOCAL,
    CLOUD;

    public static ExecutionEnvironment fromConfig(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "executionEnvironment must be explicitly configured as LOCAL or CLOUD.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown executionEnvironment '" + value
                            + "'. Supported values are LOCAL and CLOUD.",
                    exception);
        }
    }
}
