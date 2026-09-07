package problem;

import java.util.Locale;

/**
 * Distance convention defined by a benchmark instance.
 */
public enum DistanceMetric {

    /** Raw Euclidean distance. */
    RAW_EUCLIDEAN("RAW_EUCLIDEAN"),

    /** 3M convention: floor(Euclidean distance / divisor). */
    FLOOR_EUCLIDEAN_DIVISOR("FLOOR_EUCLIDEAN_DIVISOR");

    private final String instanceName;

    DistanceMetric(String instanceName) {
        this.instanceName = instanceName;
    }

    public double apply(double euclideanDistance, double divisor) {
        return switch (this) {
            case RAW_EUCLIDEAN -> euclideanDistance;
            case FLOOR_EUCLIDEAN_DIVISOR -> Math.floor(euclideanDistance / divisor);
        };
    }

    public static DistanceMetric fromInstance(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Distance Metric must be defined in the instance file.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DistanceMetric metric : values()) {
            if (metric.instanceName.equals(normalized)) {
                return metric;
            }
        }
        throw new IllegalArgumentException(
                "Unknown instance Distance Metric '" + value
                        + "'. Supported values are RAW_EUCLIDEAN and FLOOR_EUCLIDEAN_DIVISOR.");
    }
}
