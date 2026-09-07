package problem;

import java.util.Locale;

/** Basis used for a vehicle fixed cost in the benchmark instance. */
public enum FixedCostBasis {
    PER_DAY,
    PER_TOUR;

    public static FixedCostBasis fromInstance(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Fixed cost basis cannot be blank.");
        }
        try {
            return FixedCostBasis.valueOf(
                    value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown fixed cost basis '" + value
                            + "'. Use PER_DAY or PER_TOUR.",
                    exception);
        }
    }
}
