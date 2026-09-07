package stochastic;

/** Immutable result of the independent final Monte Carlo validation. */
public final class StochasticMonteCarloResult {

    private final boolean completedSuccessfully;
    private final String message;
    private final int replications;
    private final double expectedFailureCost;
    private final double averageSuccessPercent;
    private final double minimumSuccessPercent;
    private final int customersBelowRequiredSuccess;
    private final double meanAbsoluteFailureProbabilityError;
    private final double maximumAbsoluteFailureProbabilityError;
    private final long runtimeMillis;

    private StochasticMonteCarloResult(
            boolean completedSuccessfully,
            String message,
            int replications,
            double expectedFailureCost,
            double averageSuccessPercent,
            double minimumSuccessPercent,
            int customersBelowRequiredSuccess,
            double meanAbsoluteFailureProbabilityError,
            double maximumAbsoluteFailureProbabilityError,
            long runtimeMillis) {
        this.completedSuccessfully = completedSuccessfully;
        this.message = message == null ? "" : message;
        this.replications = replications;
        this.expectedFailureCost = expectedFailureCost;
        this.averageSuccessPercent = averageSuccessPercent;
        this.minimumSuccessPercent = minimumSuccessPercent;
        this.customersBelowRequiredSuccess = customersBelowRequiredSuccess;
        this.meanAbsoluteFailureProbabilityError = meanAbsoluteFailureProbabilityError;
        this.maximumAbsoluteFailureProbabilityError = maximumAbsoluteFailureProbabilityError;
        this.runtimeMillis = runtimeMillis;
    }

    public static StochasticMonteCarloResult success(
            int replications,
            double expectedFailureCost,
            double averageSuccessPercent,
            double minimumSuccessPercent,
            int customersBelowRequiredSuccess,
            double meanAbsoluteFailureProbabilityError,
            double maximumAbsoluteFailureProbabilityError,
            long runtimeMillis) {
        return new StochasticMonteCarloResult(
                true,
                "OK",
                replications,
                expectedFailureCost,
                averageSuccessPercent,
                minimumSuccessPercent,
                customersBelowRequiredSuccess,
                meanAbsoluteFailureProbabilityError,
                maximumAbsoluteFailureProbabilityError,
                runtimeMillis);
    }

    public static StochasticMonteCarloResult failure(
            int replications,
            String message,
            long runtimeMillis) {
        return new StochasticMonteCarloResult(
                false,
                message,
                replications,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                -1,
                Double.NaN,
                Double.NaN,
                runtimeMillis);
    }

    public boolean isCompletedSuccessfully() { return completedSuccessfully; }
    public String getMessage() { return message; }
    public int getReplications() { return replications; }
    public double getExpectedFailureCost() { return expectedFailureCost; }
    public double getAverageSuccessPercent() { return averageSuccessPercent; }
    public double getMinimumSuccessPercent() { return minimumSuccessPercent; }
    public int getCustomersBelowRequiredSuccess() { return customersBelowRequiredSuccess; }
    public double getMeanAbsoluteFailureProbabilityError() { return meanAbsoluteFailureProbabilityError; }
    public double getMaximumAbsoluteFailureProbabilityError() { return maximumAbsoluteFailureProbabilityError; }
    public long getRuntimeMillis() { return runtimeMillis; }
}
