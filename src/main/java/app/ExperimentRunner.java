package app;

import config.ExperimentParameters;
import experiment.ExperimentReporter;
import experiment.RunContext;
import experiment.RunResult;
import experiment.SearchProblemTemplates;
import optimization.ExactScheduleResult;
import problem.ProblemInstance;
import search.SearchEngine;
import solution.Solution;
import stochastic.StochasticMonteCarloResult;
import validation.SolutionValidationException;

/** Runs the repeated search experiments for one problem instance. */
final class ExperimentRunner {

    private final ExperimentReporter reporter;

    ExperimentRunner(ExperimentReporter reporter) {
        this.reporter = reporter;
    }

    void run(ProblemInstance problem, SearchProblemTemplates templates, String instanceId) {
        for (int runIndex = 0; runIndex < ExperimentParameters.numberOfRuns; runIndex++) {
            runOnce(problem, templates, instanceId, runIndex);
        }
    }

    private void runOnce(
            ProblemInstance problem,
            SearchProblemTemplates templates,
            String instanceId,
            int runIndex) {
        int runNumber = runIndex + 1;
        int searchSeed = ExperimentParameters.searchSeedStart + runIndex;
        RunContext runContext = new RunContext(searchSeed, reporter);
        SearchEngine searchEngine = null;
        long runStartMillis = System.currentTimeMillis();

        reporter.beginRun(instanceId, runNumber, searchSeed);

        try {
            searchEngine = new SearchEngine(problem, templates, runContext);
            searchEngine.execute();

            if (!hasValidFinalSolution(searchEngine)) {
                throw new SearchEngine.NoInitialSolutionException(
                        "No valid final solution was produced.");
            }

            recordPostSearchValidation(searchEngine);
            requireDeterministicExactCertificate(searchEngine);

            RunResult result = createRunResult(
                    instanceId, runNumber, searchSeed, searchEngine, true, "");
            reporter.completeRun(result, searchEngine);

        } catch (RuntimeException exception) {
            double runtimeSeconds = searchEngine == null
                    ? elapsedSeconds(runStartMillis, System.currentTimeMillis())
                    : searchEngine.currentRuntimeSeconds();

            if (!(exception instanceof SearchEngine.NoInitialSolutionException)
                    && !(exception instanceof SolutionValidationException)) {
                reporter.recordRunException(exception);
            }

            String message = failureMessage(exception);
            RunResult result;
            if (searchEngine != null
                    && searchEngine.searchCompleted
                    && hasValidFinalSolution(searchEngine)) {
                result = createRunResult(
                        instanceId, runNumber, searchSeed, searchEngine, false, message);
            } else {
                result = RunResult.failed(
                        instanceId, runNumber, searchSeed, runtimeSeconds, message);
            }
            reporter.completeRun(result, searchEngine);
        }
    }

    private RunResult createRunResult(
            String instanceId,
            int runNumber,
            int searchSeed,
            SearchEngine searchEngine,
            boolean success,
            String message) {
        Solution finalSolution = searchEngine.finalSolution;
        double initialObjective = searchEngine.initialSolution == null
                ? Double.NaN : searchEngine.initialSolution.objective;
        double improvementPercent = Double.NaN;
        if (Double.isFinite(initialObjective) && initialObjective != 0.0) {
            improvementPercent = (initialObjective - finalSolution.objective)
                    / Math.abs(initialObjective) * 100.0;
        }

        if (success) {
            return RunResult.success(
                    instanceId,
                    runNumber,
                    searchSeed,
                    searchEngine.finalMonteCarloValidationResult,
                    finalSolution.objective,
                    finalSolution.deterministicCost,
                    finalSolution.recourseCost,
                    ExperimentReporter.averageSuccessPercent(finalSolution),
                    ExperimentReporter.minimumSuccessPercent(finalSolution),
                    ExperimentReporter.customersBelowRequiredSuccess(finalSolution),
                    searchEngine.runtimeSeconds,
                    searchEngine.stochasticEvaluationTimeSeconds,
                    initialObjective,
                    improvementPercent,
                    searchEngine.bestTotalIteration,
                    searchEngine.timeToBestSeconds,
                    finalSolution.getMeetingPointCount(),
                    finalSolution.getUsedFirstEchelonVehicleCount(),
                    finalSolution.getUsedSecondEchelonVehicleCount(),
                    finalSolution.getTotalDistance(),
                    finalSolution.getTotalWaitingTime());
        }

        return RunResult.failedWithMetrics(
                instanceId,
                runNumber,
                searchSeed,
                searchEngine.finalMonteCarloValidationResult,
                finalSolution.objective,
                finalSolution.deterministicCost,
                finalSolution.recourseCost,
                ExperimentReporter.averageSuccessPercent(finalSolution),
                ExperimentReporter.minimumSuccessPercent(finalSolution),
                ExperimentReporter.customersBelowRequiredSuccess(finalSolution),
                searchEngine.currentRuntimeSeconds(),
                searchEngine.stochasticEvaluationTimeSeconds,
                initialObjective,
                improvementPercent,
                searchEngine.bestTotalIteration,
                searchEngine.timeToBestSeconds,
                finalSolution.getMeetingPointCount(),
                finalSolution.getUsedFirstEchelonVehicleCount(),
                finalSolution.getUsedSecondEchelonVehicleCount(),
                finalSolution.getTotalDistance(),
                finalSolution.getTotalWaitingTime(),
                message);
    }

    private void recordPostSearchValidation(SearchEngine searchEngine) {
        ExactScheduleResult exact = searchEngine.finalExactVerificationResult;
        if (ExperimentParameters.validateFinalExactSchedule && exact != null) {
            reporter.recordExactVerification(exact);
            ExactScheduleResult freeExact = searchEngine.finalFreeExactDiagnosticResult;
            if (freeExact != null && !"NOT_RUN".equals(freeExact.getMessage())) {
                reporter.recordExactVerification("Free exact comparison", freeExact);
            }
        }

        StochasticMonteCarloResult monteCarlo = searchEngine.finalMonteCarloValidationResult;
        if (ExperimentParameters.validateFinalMonteCarlo && monteCarlo != null) {
            reporter.recordMonteCarloValidation(monteCarlo);
        }
    }

    private static void requireDeterministicExactCertificate(SearchEngine searchEngine) {
        if (!ExperimentParameters.validateFinalExactSchedule
                || !ExperimentParameters.isDeterministicEvaluation()) {
            return;
        }
        ExactScheduleResult result = searchEngine.finalExactVerificationResult;
        if (result != null && result.isCertifiedFeasible()) {
            return;
        }
        String detail = result == null ? "no verification result was produced" : result.getMessage();
        throw new SolutionValidationException(
                "Final exact schedule verification did not certify feasibility: " + detail);
    }

    private static boolean hasValidFinalSolution(SearchEngine searchEngine) {
        return searchEngine != null
                && searchEngine.finalSolution != null
                && searchEngine.finalSolution.completionStatus == 2
                && searchEngine.finalSolution.feasibilityStatus == 0
                && Double.isFinite(searchEngine.finalSolution.objective)
                && searchEngine.finalSolution.objective < Integer.MAX_VALUE;
    }

    private static String failureMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message;
    }

    private static double elapsedSeconds(long startMillis, long endMillis) {
        return (endMillis - startMillis) / 1000.0;
    }
}
