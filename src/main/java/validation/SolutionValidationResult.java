package validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a structural solution check.
 */
public final class SolutionValidationResult {

    private final List<String> errors;

    SolutionValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public String summary() {
        if (isValid()) {
            return "PASSED";
        }
        return "FAILED (" + errors.size() + " issue" + (errors.size() == 1 ? "" : "s") + ")";
    }

    public String formatErrors() {
        if (isValid()) {
            return "PASSED";
        }
        StringBuilder outputBuilder = new StringBuilder();
        for (String error : errors) {
            outputBuilder.append("  - ").append(error).append(System.lineSeparator());
        }
        return outputBuilder.toString().stripTrailing();
    }
}
