package experiment;

import config.ExperimentParameters;
import stochastic.StochasticMonteCarloResult;

/**
 * Run-level result used by the experiment reports.
 * Search metrics are retained if a later final check fails.
 *
 * Run status describes whether the computational run itself completed.
 * Final validation status separately describes the independent final Monte Carlo
 * validation of the final solution.
 */
public final class RunResult {

    private final String instance;
    private final int runNumber;
    private final int seed;
    private final String status;
    private final String finalValidationStatus;
    private final int finalValidationReplications;
    private final double finalValidationAverageSuccessPercent;
    private final double finalValidationMinimumSuccessPercent;
    private final int finalValidationCustomersBelowRequiredSuccess;
    private final String finalValidationMessage;
    private final boolean searchMetricsAvailable;
    private final double objective;
    private final double deterministicObjective;
    private final double recourseCost;
    private final double averageSuccessPercent;
    private final double minimumSuccessPercent;
    private final int customersBelowRequiredSuccess;
    private final double runtimeSeconds;
    private final double stochasticEvaluationTimeSeconds;
    private final double initialObjective;
    private final double improvementPercent;
    private final int bestIteration;
    private final double timeToBestSeconds;
    private final int meetingEventCount;
    private final int firstEchelonVehicleCount;
    private final int secondEchelonVehicleCount;
    private final double totalDistance;
    private final double totalWaitingTime;
    private final String message;

    private RunResult(
            String instance,
            int runNumber,
            int seed,
            String status,
            String finalValidationStatus,
            int finalValidationReplications,
            double finalValidationAverageSuccessPercent,
            double finalValidationMinimumSuccessPercent,
            int finalValidationCustomersBelowRequiredSuccess,
            String finalValidationMessage,
            boolean searchMetricsAvailable,
            double objective,
            double deterministicObjective,
            double recourseCost,
            double averageSuccessPercent,
            double minimumSuccessPercent,
            int customersBelowRequiredSuccess,
            double runtimeSeconds,
            double stochasticEvaluationTimeSeconds,
            double initialObjective,
            double improvementPercent,
            int bestIteration,
            double timeToBestSeconds,
            int meetingEventCount,
            int firstEchelonVehicleCount,
            int secondEchelonVehicleCount,
            double totalDistance,
            double totalWaitingTime,
            String message) {
        this.instance = instance;
        this.runNumber = runNumber;
        this.seed = seed;
        this.status = status;
        this.finalValidationStatus = finalValidationStatus == null ? "NOT_RUN" : finalValidationStatus;
        this.finalValidationReplications = finalValidationReplications;
        this.finalValidationAverageSuccessPercent = finalValidationAverageSuccessPercent;
        this.finalValidationMinimumSuccessPercent = finalValidationMinimumSuccessPercent;
        this.finalValidationCustomersBelowRequiredSuccess = finalValidationCustomersBelowRequiredSuccess;
        this.finalValidationMessage = finalValidationMessage == null ? "" : finalValidationMessage;
        this.searchMetricsAvailable = searchMetricsAvailable;
        this.objective = objective;
        this.deterministicObjective = deterministicObjective;
        this.recourseCost = recourseCost;
        this.averageSuccessPercent = averageSuccessPercent;
        this.minimumSuccessPercent = minimumSuccessPercent;
        this.customersBelowRequiredSuccess = customersBelowRequiredSuccess;
        this.runtimeSeconds = runtimeSeconds;
        this.stochasticEvaluationTimeSeconds = stochasticEvaluationTimeSeconds;
        this.initialObjective = initialObjective;
        this.improvementPercent = improvementPercent;
        this.bestIteration = bestIteration;
        this.timeToBestSeconds = timeToBestSeconds;
        this.meetingEventCount = meetingEventCount;
        this.firstEchelonVehicleCount = firstEchelonVehicleCount;
        this.secondEchelonVehicleCount = secondEchelonVehicleCount;
        this.totalDistance = totalDistance;
        this.totalWaitingTime = totalWaitingTime;
        this.message = message == null ? "" : message;
    }

    public static RunResult success(
            String instance,
            int runNumber,
            int seed,
            StochasticMonteCarloResult finalValidation,
            double objective,
            double deterministicObjective,
            double recourseCost,
            double averageSuccessPercent,
            double minimumSuccessPercent,
            int customersBelowRequiredSuccess,
            double runtimeSeconds,
            double stochasticEvaluationTimeSeconds,
            double initialObjective,
            double improvementPercent,
            int bestIteration,
            double timeToBestSeconds,
            int meetingEventCount,
            int firstEchelonVehicleCount,
            int secondEchelonVehicleCount,
            double totalDistance,
            double totalWaitingTime) {
        return completedSearch(
                instance, runNumber, seed, "SUCCESS", finalValidation,
                objective, deterministicObjective, recourseCost,
                averageSuccessPercent, minimumSuccessPercent, customersBelowRequiredSuccess,
                runtimeSeconds, stochasticEvaluationTimeSeconds,
                initialObjective, improvementPercent, bestIteration, timeToBestSeconds,
                meetingEventCount, firstEchelonVehicleCount, secondEchelonVehicleCount,
                totalDistance, totalWaitingTime, "");
    }

    public static RunResult failedWithMetrics(
            String instance,
            int runNumber,
            int seed,
            StochasticMonteCarloResult finalValidation,
            double objective,
            double deterministicObjective,
            double recourseCost,
            double averageSuccessPercent,
            double minimumSuccessPercent,
            int customersBelowRequiredSuccess,
            double runtimeSeconds,
            double stochasticEvaluationTimeSeconds,
            double initialObjective,
            double improvementPercent,
            int bestIteration,
            double timeToBestSeconds,
            int meetingEventCount,
            int firstEchelonVehicleCount,
            int secondEchelonVehicleCount,
            double totalDistance,
            double totalWaitingTime,
            String message) {
        return completedSearch(
                instance, runNumber, seed, "FAILED", finalValidation,
                objective, deterministicObjective, recourseCost,
                averageSuccessPercent, minimumSuccessPercent, customersBelowRequiredSuccess,
                runtimeSeconds, stochasticEvaluationTimeSeconds,
                initialObjective, improvementPercent, bestIteration, timeToBestSeconds,
                meetingEventCount, firstEchelonVehicleCount, secondEchelonVehicleCount,
                totalDistance, totalWaitingTime, message);
    }

    private static RunResult completedSearch(
            String instance,
            int runNumber,
            int seed,
            String status,
            StochasticMonteCarloResult finalValidation,
            double objective,
            double deterministicObjective,
            double recourseCost,
            double averageSuccessPercent,
            double minimumSuccessPercent,
            int customersBelowRequiredSuccess,
            double runtimeSeconds,
            double stochasticEvaluationTimeSeconds,
            double initialObjective,
            double improvementPercent,
            int bestIteration,
            double timeToBestSeconds,
            int meetingEventCount,
            int firstEchelonVehicleCount,
            int secondEchelonVehicleCount,
            double totalDistance,
            double totalWaitingTime,
            String message) {
        return new RunResult(
                instance, runNumber, seed, status,
                classifyFinalValidation(finalValidation),
                finalValidation == null ? -1 : finalValidation.getReplications(),
                finalValidation == null ? Double.NaN : finalValidation.getAverageSuccessPercent(),
                finalValidation == null ? Double.NaN : finalValidation.getMinimumSuccessPercent(),
                finalValidation == null ? -1 : finalValidation.getCustomersBelowRequiredSuccess(),
                finalValidation == null ? "" : finalValidation.getMessage(),
                true,
                objective, deterministicObjective, recourseCost,
                averageSuccessPercent, minimumSuccessPercent, customersBelowRequiredSuccess,
                runtimeSeconds, stochasticEvaluationTimeSeconds,
                initialObjective, improvementPercent, bestIteration, timeToBestSeconds,
                meetingEventCount, firstEchelonVehicleCount, secondEchelonVehicleCount,
                totalDistance, totalWaitingTime, message);
    }

    public static RunResult failed(
            String instance,
            int runNumber,
            int seed,
            double runtimeSeconds,
            String message) {
        return new RunResult(
                instance, runNumber, seed, "FAILED",
                ExperimentParameters.isStochasticEvaluation() ? "NOT_RUN" : "N/A",
                -1, Double.NaN, Double.NaN, -1, "",
                false,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, -1,
                runtimeSeconds, Double.NaN,
                Double.NaN, Double.NaN,
                -1, Double.NaN,
                -1, -1, -1,
                Double.NaN, Double.NaN, message);
    }

    public static String classifyFinalValidation(StochasticMonteCarloResult result) {
        if (!ExperimentParameters.validateFinalMonteCarlo || result == null) {
            return "NOT_RUN";
        }
        if (!result.isCompletedSuccessfully()) {
            return "ERROR";
        }
        if (ExperimentParameters.isStochasticEvaluation() && ExperimentParameters.usesCcm()) {
            return result.getCustomersBelowRequiredSuccess() == 0 ? "PASS" : "FAIL";
        }
        return "COMPLETE";
    }

    public String getInstance() { return instance; }
    public int getRunNumber() { return runNumber; }
    public int getSeed() { return seed; }
    public String getStatus() { return status; }
    public boolean isSuccess() { return "SUCCESS".equals(status); }
    public String getFinalValidationStatus() { return finalValidationStatus; }
    public boolean isFinalValidationPass() { return "PASS".equals(finalValidationStatus); }
    public boolean isFinalValidationFail() { return "FAIL".equals(finalValidationStatus); }
    public boolean isFinalValidationError() { return "ERROR".equals(finalValidationStatus); }
    public boolean isFinalValidationNotRun() { return "NOT_RUN".equals(finalValidationStatus); }
    public boolean hasCompletedFinalValidation() {
        return "PASS".equals(finalValidationStatus)
                || "FAIL".equals(finalValidationStatus)
                || "COMPLETE".equals(finalValidationStatus);
    }
    public int getFinalValidationReplications() { return finalValidationReplications; }
    public double getFinalValidationAverageSuccessPercent() { return finalValidationAverageSuccessPercent; }
    public double getFinalValidationMinimumSuccessPercent() { return finalValidationMinimumSuccessPercent; }
    public int getFinalValidationCustomersBelowRequiredSuccess() { return finalValidationCustomersBelowRequiredSuccess; }
    public String getFinalValidationMessage() { return finalValidationMessage; }
    public boolean hasSearchMetrics() { return searchMetricsAvailable; }
    public double getObjective() { return objective; }
    public double getDeterministicObjective() { return deterministicObjective; }
    public double getRecourseCost() { return recourseCost; }
    public double getAverageSuccessPercent() { return averageSuccessPercent; }
    public double getMinimumSuccessPercent() { return minimumSuccessPercent; }
    public int getCustomersBelowRequiredSuccess() { return customersBelowRequiredSuccess; }
    public double getRuntimeSeconds() { return runtimeSeconds; }
    public double getStochasticEvaluationTimeSeconds() { return stochasticEvaluationTimeSeconds; }
    public double getStochasticRuntimePercent() {
        if (!Double.isFinite(stochasticEvaluationTimeSeconds)
                || !Double.isFinite(runtimeSeconds)
                || runtimeSeconds <= 0.0) {
            return Double.NaN;
        }
        return stochasticEvaluationTimeSeconds / runtimeSeconds * 100.0;
    }
    public double getInitialObjective() { return initialObjective; }
    public double getImprovementPercent() { return improvementPercent; }
    public int getBestIteration() { return bestIteration; }
    public double getTimeToBestSeconds() { return timeToBestSeconds; }
    public int getMeetingEventCount() { return meetingEventCount; }
    public int getFirstEchelonVehicleCount() { return firstEchelonVehicleCount; }
    public int getSecondEchelonVehicleCount() { return secondEchelonVehicleCount; }
    public double getTotalDistance() { return totalDistance; }
    public double getTotalWaitingTime() { return totalWaitingTime; }
    public String getMessage() { return message; }
}
