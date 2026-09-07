package validation;

/** Raised when a configured validation checkpoint detects an invalid solution. */
public final class SolutionValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SolutionValidationException(String message) {
        super(message);
    }
}
