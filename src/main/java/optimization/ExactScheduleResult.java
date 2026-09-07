package optimization;

import solution.Solution;

/** Result of one exact deterministic schedule optimization or verification. */
public final class ExactScheduleResult {

    private final boolean completedSuccessfully;
    private final Boolean certifiedFeasible;
    private final boolean fixedDepartures;
    private final int solverStatus;
    private final double objective;
    private final long runtimeMillis;
    private final String message;
    private final ScheduleSnapshot scheduleSnapshot;
    private final Solution verifiedSolution;

    private ExactScheduleResult(
            boolean completedSuccessfully,
            Boolean certifiedFeasible,
            boolean fixedDepartures,
            int solverStatus,
            double objective,
            long runtimeMillis,
            String message,
            ScheduleSnapshot scheduleSnapshot,
            Solution verifiedSolution) {
        this.completedSuccessfully = completedSuccessfully;
        this.certifiedFeasible = certifiedFeasible;
        this.fixedDepartures = fixedDepartures;
        this.solverStatus = solverStatus;
        this.objective = objective;
        this.runtimeMillis = runtimeMillis;
        this.message = message == null ? "" : message;
        this.scheduleSnapshot = scheduleSnapshot;
        this.verifiedSolution = verifiedSolution;
    }
    public static ExactScheduleResult optimal(
            boolean fixedDepartures,
            int solverStatus,
            double objective,
            long runtimeMillis,
            ScheduleSnapshot scheduleSnapshot,
            Solution verifiedSolution) {
        return new ExactScheduleResult(
                true,
                Boolean.TRUE,
                fixedDepartures,
                solverStatus,
                objective,
                runtimeMillis,
                "OPTIMAL",
                scheduleSnapshot,
                verifiedSolution);
    }

    public static ExactScheduleResult infeasible(
            boolean fixedDepartures,
            int solverStatus,
            long runtimeMillis,
            String message) {
        return new ExactScheduleResult(
                true,
                Boolean.FALSE,
                fixedDepartures,
                solverStatus,
                Double.NaN,
                runtimeMillis,
                message,
                null,
                null);
    }

    public static ExactScheduleResult notOptimal(
            boolean fixedDepartures,
            int solverStatus,
            long runtimeMillis,
            String message) {
        return new ExactScheduleResult(
                true,
                null,
                fixedDepartures,
                solverStatus,
                Double.NaN,
                runtimeMillis,
                message,
                null,
                null);
    }

    public static ExactScheduleResult operationalFailure(
            boolean fixedDepartures,
            long runtimeMillis,
            String message) {
        return new ExactScheduleResult(
                false,
                null,
                fixedDepartures,
                -1,
                Double.NaN,
                runtimeMillis,
                message,
                null,
                null);
    }

    public static ExactScheduleResult skipped(String message) {
        return new ExactScheduleResult(
                false,
                null,
                false,
                -1,
                Double.NaN,
                0L,
                message,
                null,
                null);
    }

    public boolean isCompletedSuccessfully() { return completedSuccessfully; }
    public boolean isCertifiedFeasible() { return Boolean.TRUE.equals(certifiedFeasible); }
    public boolean hasFeasibilityCertificate() { return certifiedFeasible != null; }
    public boolean usesFixedDepartures() { return fixedDepartures; }
    public int getSolverStatus() { return solverStatus; }
    public double getObjective() { return objective; }
    public long getRuntimeMillis() { return runtimeMillis; }
    public String getMessage() { return message; }
    public ScheduleSnapshot getScheduleSnapshot() { return scheduleSnapshot; }

    /**
     * Returns the independent exact-schedule copy used by verification calls.
     * For optimizeSchedule(), this is the supplied solution itself. Callers
     * must treat the returned object as diagnostic data and avoid mutating the
     * search-owned final solution.
     */
    public Solution getVerifiedSolution() { return verifiedSolution; }
}
