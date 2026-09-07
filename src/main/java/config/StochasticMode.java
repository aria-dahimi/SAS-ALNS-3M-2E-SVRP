package config;

import java.util.Locale;

/** Stochastic optimization formulation used when evaluationMode=STOCHASTIC. */
public enum StochasticMode {
    CCM,
    PBM;

    public static StochasticMode fromConfig(String value) {
        if (value == null) {
            throw new IllegalArgumentException("stochasticMode is required when evaluationMode=STOCHASTIC.");
        }
        try {
            return StochasticMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "stochasticMode must be CCM or PBM, but was: " + value,
                    exception);
        }
    }
}
