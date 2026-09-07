package problem;

import java.util.Locale;

/**
 * Supported problem variants.
 * 3M-2E-VRP uses mobile transfer locations; DELLAERT-2E-VRP uses fixed satellites.
 */
public enum ProblemVariant {

    THREE_M_2E_VRP("3M-2E-VRP"),
    DELLAERT_2E_VRP("DELLAERT-2E-VRP");

    private final String configName;

    ProblemVariant(String configName) {
        this.configName = configName;
    }

    /** Name used in config.properties, reports, and the input subdirectory. */
    public String configName() {
        return configName;
    }

    /** Input subdirectory under the configured input root. */
    public String inputDirectoryName() {
        return configName;
    }

    public boolean isDellaert() {
        return this == DELLAERT_2E_VRP;
    }

    /**
     * Parses the two public problem names accepted by config.properties.
     */
    public static ProblemVariant fromConfig(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "problemVariant must be explicitly configured as 3M-2E-VRP or DELLAERT-2E-VRP.");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ProblemVariant candidateVariant : values()) {
            if (candidateVariant.configName.toUpperCase(Locale.ROOT).equals(normalized)) {
                return candidateVariant;
            }
        }

        throw new IllegalArgumentException(
                "Unknown problemVariant '" + value
                        + "'. Supported values are 3M-2E-VRP and DELLAERT-2E-VRP.");
    }
}
