package config;

import java.util.Locale;

/** Top-level candidate-evaluation regime used by the search. */
public enum EvaluationMode {
    DETERMINISTIC,
    STOCHASTIC;

    public static EvaluationMode fromConfig(String value) {
        if (value == null) {
            throw new IllegalArgumentException("evaluationMode is required.");
        }
        try {
            return EvaluationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "evaluationMode must be DETERMINISTIC or STOCHASTIC, but was: " + value,
                    exception);
        }
    }
}
