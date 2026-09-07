package experiment;
import config.ExperimentParameters;
import config.ProblemParameters;
import config.ProjectSettings;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import search.SearchEngine;
import solution.Solution;
import problem.Customer;
import problem.ProblemInstance;
import validation.SolutionValidationResult;
import optimization.ExactScheduleResult;
import optimization.ScheduleSnapshot;
import stochastic.StochasticMonteCarloResult;

/**
 * Writes the live console log, cumulative experiment report, run CSV, and best solutions.
 * Search times are reported as elapsed runtime.
 */
public final class ExperimentReporter implements AutoCloseable {

    private static final String RUNS_HEADER = String.join(",",
            "instance", "run", "seed", "run_status", "final_validation_status", "search_metrics_available",
            "objective", "deterministic_cost", "deterministic_time_mode", "failure_cost",
            "search_avg_success_pct", "search_min_success_pct", "search_below_required",
            "final_validation_replications", "final_validation_avg_success_pct",
            "final_validation_min_success_pct", "final_validation_below_required",
            "events", "fev", "sev", "waiting_time", "total_distance",
            "initial_objective", "improvement_pct",
            "best_sa_cycle", "best_lns_iteration", "best_total_iteration",
            "time_to_best_sec", "stochastic_evaluation_time_sec", "stochastic_runtime_pct",
            "runtime_sec", "final_validation_message", "message");

    private static final String VALIDATION_HEADER = String.join(",",
            "instance", "run", "seed",
            "exact_enabled", "exact_completed", "exact_certified", "exact_fixed_departures",
            "exact_solver_status", "exact_objective", "exact_operating_cost",
            "exact_waiting_time", "exact_waiting_cost", "exact_runtime_ms", "exact_message",
            "free_exact_completed", "free_exact_certified", "free_exact_solver_status",
            "free_exact_operating_cost", "free_exact_waiting_time", "free_exact_waiting_cost",
            "free_exact_runtime_ms", "free_exact_message",
            "final_mc_enabled", "final_mc_completed", "final_mc_status", "final_mc_replications",
            "final_mc_expected_failure_cost", "final_mc_avg_success_pct",
            "final_mc_min_success_pct", "final_mc_below_required",
            "final_mc_mae_failure_probability", "final_mc_max_abs_failure_probability_error",
            "final_mc_runtime_ms", "final_mc_message");

    private static final String FINAL_EXACT_DIAGNOSTIC_HEADER = String.join(",",
            "instance", "run", "seed", "evaluation", "diagnostic_classification",
            "search_operating_cost", "search_waiting_time", "search_waiting_cost",
            "primary_fixed_departures", "primary_completed", "primary_certified",
            "primary_solver_status", "primary_operating_cost", "primary_waiting_time",
            "primary_waiting_cost", "primary_runtime_ms", "primary_message",
            "search_vs_primary_abs_operating_cost_error", "search_vs_primary_abs_waiting_error",
            "free_completed", "free_certified", "free_solver_status",
            "free_operating_cost", "free_waiting_time", "free_waiting_cost",
            "free_runtime_ms", "free_message",
            "primary_vs_free_operating_cost_gap", "primary_vs_free_waiting_gap",
            "search_stochastic_objective", "search_failure_cost", "search_avg_success_pct",
            "search_min_success_pct", "search_below_required",
            "primary_stochastic_objective", "primary_failure_cost", "primary_avg_success_pct",
            "primary_min_success_pct", "primary_below_required",
            "free_stochastic_objective", "free_failure_cost", "free_avg_success_pct",
            "free_min_success_pct", "free_below_required");

    private static final String SCHEDULING_VEHICLE_DETAIL_HEADER = String.join(",",
            "instance", "run", "seed", "context", "sequence", "echelon", "vehicle_id",
            "origin_id", "approximate_departure", "exact_departure", "departure_difference",
            "approximate_waiting_time", "exact_waiting_time", "waiting_time_difference",
            "approximate_waiting_cost", "exact_waiting_cost", "waiting_cost_difference",
            "first_node_id", "first_lower_bound", "first_upper_bound",
            "approximate_first_visit", "exact_first_visit", "first_visit_difference");

    private static final String SCHEDULING_NODE_DETAIL_HEADER = String.join(",",
            "instance", "run", "seed", "context", "sequence", "echelon", "vehicle_id",
            "position", "approximate_node_id", "exact_node_id", "node_type",
            "ready_time", "due_time", "lower_bound", "upper_bound",
            "approximate_arrival", "exact_arrival", "arrival_difference",
            "approximate_visit", "exact_visit", "visit_difference",
            "approximate_waiting", "exact_waiting", "waiting_difference");

    private static final String SCHEDULING_ARC_DETAIL_HEADER = String.join(",",
            "instance", "run", "seed", "context", "sequence", "echelon", "vehicle_id",
            "position", "approximate_from", "approximate_to", "exact_from", "exact_to",
            "travel_time", "service_time_from",
            "approximate_from_time", "exact_from_time", "from_time_difference",
            "approximate_arrival", "exact_arrival", "arrival_difference",
            "approximate_visit", "exact_visit", "visit_difference",
            "approximate_waiting", "exact_waiting", "waiting_difference",
            "approximate_waiting_cost", "exact_waiting_cost", "waiting_cost_difference");

    private static final String STOCHASTIC_SEARCH_HEADER = String.format(Locale.US,
            "%-7s %3s %8s %10s %13s %13s %7s %13s %12s %7s %7s %5s %4s %4s %4s %9s %10s %7s %7s %7s %s",
            "Phase", "SA", "Step", "Time", "Current", "Best", "Gap%", "BestDet", "BestFail",
            "AvgS%", "MinS%", "B<α", "Ev", "FEV", "SEV", "Wait", "Temp",
            "Feas%", "Acc%", "Stag", "Event");

    private static final String DETERMINISTIC_SEARCH_HEADER = String.format(Locale.US,
            "%-7s %3s %8s %10s %13s %13s %7s %4s %4s %4s %9s %10s %7s %7s %7s %s",
            "Phase", "SA", "Step", "Time", "Current", "Best", "Gap%",
            "Ev", "FEV", "SEV", "Wait", "Temp", "Feas%", "Acc%", "Stag", "Event");

    private final ProjectSettings projectSettings;
    private final Path batchDirectory;
    private final Path runsCsv;
    private final Path validationCsv;
    private final Path diagnosticsDirectory;
    private final Path finalExactDiagnosticsCsv;
    private final Path schedulingVehicleDetailsCsv;
    private final Path schedulingNodeDetailsCsv;
    private final Path schedulingArcDetailsCsv;
    private final Path batchConsoleLog;
    private final Path experimentLog;
    private final Path bestSolutionsDirectory;
    private final Path runSolutionsDirectory;
    private final BufferedWriter consoleWriter;

    private final Map<String, List<RunResult>> resultsByInstance = new LinkedHashMap<>();
    private final Map<String, Double> bestObjectiveByInstance = new LinkedHashMap<>();
    private final Map<String, InstanceDefinition> instanceDefinitions = new LinkedHashMap<>();
    private final List<String> expectedInstances = new ArrayList<>();
    private final String configurationSnapshot;

    private final int runsPerInstance;
    private final int totalIterationBudget;
    private final int checkpointInterval;
    private final long experimentStartMillis;

    private String currentInstance;
    private int currentRun;
    private int currentSeed;
    private long currentRunStartMillis;
    private int currentSaCycle;
    private int currentLnsIteration;
    private int currentTotalIteration;
    private double currentObjective = Double.NaN;
    private double currentBestObjective = Double.NaN;
    private double currentInitialObjective = Double.NaN;
    private int currentBestIteration;
    private double currentBestElapsedSeconds = Double.NaN;
    private boolean isRunActive;
    private boolean isExperimentComplete;
    private boolean isClosed;

    public ExperimentReporter(Path sourceConfig, ProjectSettings projectSettings) throws IOException {
        if (projectSettings == null) {
            throw new IllegalArgumentException("Project settings cannot be null.");
        }
        this.projectSettings = projectSettings;
        this.runsPerInstance = ExperimentParameters.numberOfRuns;
        this.totalIterationBudget = ExperimentParameters.maxSaIterations
                * ExperimentParameters.maxLnsIterations;
        this.checkpointInterval = ExperimentParameters.consoleProgressInterval;
        this.experimentStartMillis = System.currentTimeMillis();
        this.configurationSnapshot = Files.readString(sourceConfig, StandardCharsets.UTF_8).stripTrailing();

        buildExpectedInstanceNames();

        Path resultRoot = projectSettings.getResultDirectory();
        Files.createDirectories(resultRoot);
        this.batchDirectory = createUniqueBatchDirectory(resultRoot, buildBatchDirectoryName());
        this.bestSolutionsDirectory = batchDirectory.resolve("best-solutions");
        Files.createDirectories(bestSolutionsDirectory);
        this.runSolutionsDirectory = batchDirectory.resolve("run-solutions");
        Files.createDirectories(runSolutionsDirectory);

        this.runsCsv = batchDirectory.resolve("runs.csv");
        this.validationCsv = batchDirectory.resolve("validation.csv");
        this.diagnosticsDirectory = batchDirectory.resolve("diagnostics");
        Files.createDirectories(diagnosticsDirectory);
        this.finalExactDiagnosticsCsv = diagnosticsDirectory.resolve("final-exact-verification.csv");
        this.schedulingVehicleDetailsCsv = diagnosticsDirectory.resolve("scheduling-vehicle-details.csv");
        this.schedulingNodeDetailsCsv = diagnosticsDirectory.resolve("scheduling-node-details.csv");
        this.schedulingArcDetailsCsv = diagnosticsDirectory.resolve("scheduling-arc-details.csv");
        this.batchConsoleLog = batchDirectory.resolve("batch-console.log");
        this.experimentLog = batchDirectory.resolve("experiment.log");

        Files.copy(sourceConfig, batchDirectory.resolve("config.properties"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(runsCsv, RUNS_HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(validationCsv, VALIDATION_HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(finalExactDiagnosticsCsv, FINAL_EXACT_DIAGNOSTIC_HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(schedulingVehicleDetailsCsv, SCHEDULING_VEHICLE_DETAIL_HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(schedulingNodeDetailsCsv, SCHEDULING_NODE_DETAIL_HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(schedulingArcDetailsCsv, SCHEDULING_ARC_DETAIL_HEADER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        this.consoleWriter = Files.newBufferedWriter(batchConsoleLog,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        writeBatchHeader();
        rewriteExperimentLog();
    }

    public boolean shouldWriteCheckpoint(int iteration) {
        return iteration > 0 && iteration % checkpointInterval == 0;
    }

    public double currentRunElapsedSeconds() {
        if (!isRunActive) {
            return 0.0;
        }
        return (System.currentTimeMillis() - currentRunStartMillis) / 1000.0;
    }

    public void beginInstance(String instance) {
        resultsByInstance.computeIfAbsent(instance, key -> new ArrayList<>());
        line("");
        line("Instance: " + instance);
        rewriteExperimentLogQuietly();
    }

    /** Capture instance-defined data that must not be confused with experiment parameters. */
    public void recordInstanceDefinition(String instance, ProblemInstance problem) {
        if (instance == null || problem == null) {
            return;
        }
        instanceDefinitions.put(instance, InstanceDefinition.capture(problem));
        line("Instance definition:");
        line("  Distance metric: " + problem.getDistanceMetric());
        if ("FLOOR_EUCLIDEAN_DIVISOR".equals(problem.getDistanceMetric().name())) {
            line(String.format(Locale.US, "  Distance divisor: %.6f", problem.getDistanceDivisor()));
        }
        rewriteExperimentLogQuietly();
    }

    public void beginRun(String instance, int runNumber, int seed) {
        this.currentInstance = instance;
        this.currentRun = runNumber;
        this.currentSeed = seed;
        this.currentRunStartMillis = System.currentTimeMillis();
        this.currentSaCycle = 0;
        this.currentLnsIteration = 0;
        this.currentTotalIteration = 0;
        this.currentObjective = Double.NaN;
        this.currentBestObjective = Double.NaN;
        this.currentInitialObjective = Double.NaN;
        this.currentBestIteration = 0;
        this.currentBestElapsedSeconds = Double.NaN;
        this.isRunActive = true;

        line("");
        line("============================================================");
        line(String.format(Locale.US, "sas-alns RUN %d OF %d", runNumber, runsPerInstance));
        line("Instance: " + instance);
        line("Search seed: " + seed);
        line("============================================================");
        rewriteExperimentLogQuietly();
    }

    public void recordInitialSolution(
            Solution solution,
            int populationSize,
            double initialTemperature,
            double finalTemperature,
            double coolingRate) {
        if (solution == null) {
            return;
        }
        this.currentInitialObjective = solution.objective;
        this.currentObjective = solution.objective;
        this.currentBestObjective = solution.objective;
        this.currentBestIteration = 0;
        this.currentBestElapsedSeconds = currentRunElapsedSeconds();

        line("");
        line("Initial solution:");
        line(String.format(Locale.US, "  Objective: %.6f", solution.objective));
        line("  Initial population: " + populationSize);
        line("Search configuration:");
        line("  Scheduling mode: " + ExperimentParameters.schedulingMode);
        line("  Final exact verification: " + ExperimentParameters.validateFinalExactSchedule);
        if (ExperimentParameters.isStochasticEvaluation()) {
            line("  Common random numbers: " + ExperimentParameters.simulationUseCommonRandomNumbers);
            line("  Search Monte Carlo seed: " + ExperimentParameters.simulationSearchSeed);
            line("  Final Monte Carlo seed: " + ExperimentParameters.finalSimulationSeed);
            line("  Final Monte Carlo validation: " + ExperimentParameters.validateFinalMonteCarlo
                    + " (n=" + ExperimentParameters.finalSimulationReplications + ")");
        }
        line("  Simulated-annealing cycles: " + ExperimentParameters.maxSaIterations);
        line("  LNS iterations per SA cycle: " + ExperimentParameters.maxLnsIterations);
        line("  Total LNS iterations: " + totalIterationBudget);
        line("  Operator repetitions: " + ExperimentParameters.operatorRepetitions);
        line(String.format(Locale.US,
                "  Initial/final temperature/cooling: %.6f / %.6f / %.12f",
                initialTemperature, finalTemperature, coolingRate));
        line("");
        writeSearchHeader();
        writeSearchRow("START", 0, 0, solution, solution,
                initialTemperature, Double.NaN, Double.NaN, 0, "Search start");
        rewriteExperimentLogQuietly();
    }

    public void recordNewBest(
            int simulatedAnnealingCycle,
            int largeNeighborhoodSearchIteration,
            int totalIteration,
            Solution bestSolution,
            double temperature,
            double feasibilityRate,
            double acceptanceRate,
            int stagnation,
            String operatorName) {
        if (!isRunActive || bestSolution == null) {
            return;
        }
        this.currentSaCycle = simulatedAnnealingCycle;
        this.currentLnsIteration = largeNeighborhoodSearchIteration;
        this.currentTotalIteration = totalIteration;
        this.currentObjective = bestSolution.objective;
        this.currentBestObjective = bestSolution.objective;
        this.currentBestIteration = totalIteration;
        this.currentBestElapsedSeconds = currentRunElapsedSeconds();

        writeSearchRow("SEARCH", simulatedAnnealingCycle, largeNeighborhoodSearchIteration, bestSolution, bestSolution,
                temperature, feasibilityRate, acceptanceRate, stagnation,
                "NEW BEST | " + operatorName);
        rewriteExperimentLogQuietly();
    }

    public void recordCheckpoint(
            int simulatedAnnealingCycle,
            int largeNeighborhoodSearchIteration,
            int totalIteration,
            Solution currentSolution,
            Solution bestSolution,
            double temperature,
            double feasibilityRate,
            double acceptanceRate,
            int stagnation) {
        if (!isRunActive || currentSolution == null || bestSolution == null) {
            return;
        }
        this.currentSaCycle = simulatedAnnealingCycle;
        this.currentLnsIteration = largeNeighborhoodSearchIteration;
        this.currentTotalIteration = totalIteration;
        this.currentObjective = currentSolution.objective;
        this.currentBestObjective = bestSolution.objective;

        writeSearchRow("SEARCH", simulatedAnnealingCycle, largeNeighborhoodSearchIteration, currentSolution, bestSolution,
                temperature, feasibilityRate, acceptanceRate, stagnation, "Checkpoint");
        rewriteExperimentLogQuietly();
    }

    public void recordValidation(
            String label,
            SolutionValidationResult validation,
            double elapsedMillis) {
        if (validation == null) {
            return;
        }
        line(String.format(Locale.US,
                "Validation | %-18s | %-16s | %.3f ms",
                label, validation.summary(), elapsedMillis));
        if (!validation.isValid()) {
            for (String error : validation.getErrors()) {
                line("  - " + error);
            }
        }
        rewriteExperimentLogQuietly();
    }

    public void recordExactVerification(ExactScheduleResult result) {
        recordExactVerification("Exact verification", result);
    }

    public void recordExactVerification(String label, ExactScheduleResult result) {
        if (result == null) {
            return;
        }
        ScheduleSnapshot schedule = result.getScheduleSnapshot();
        line(String.format(Locale.US,
                "%s | completed=%s | certified=%s | fixed departures=%s | solver status=%d | objective=%s | operating cost=%s | waiting=%s | %.3f ms | %s",
                label,
                result.isCompletedSuccessfully(),
                result.hasFeasibilityCertificate()
                        ? Boolean.toString(result.isCertifiedFeasible())
                        : "N/A",
                result.usesFixedDepartures(),
                result.getSolverStatus(),
                number(result.getObjective()),
                schedule == null ? "" : number(schedule.getDeterministicOperatingCost()),
                schedule == null ? "" : number(schedule.getTotalWaitingTime()),
                (double) result.getRuntimeMillis(),
                result.getMessage()));
        rewriteExperimentLogQuietly();
    }

    public void recordMonteCarloValidation(StochasticMonteCarloResult result) {
        if (result == null) {
            return;
        }
        String finalValidationStatus = RunResult.classifyFinalValidation(result);
        if (!result.isCompletedSuccessfully()) {
            line("Final Monte Carlo validation | " + finalValidationStatus + " | " + result.getMessage());
        } else {
            line(String.format(Locale.US,
                    "Final Monte Carlo validation | %s | n=%d | avg success=%.2f%% | min success=%.2f%% | below required=%d | failure cost=%.6f | %.3f ms",
                    finalValidationStatus,
                    result.getReplications(),
                    result.getAverageSuccessPercent(),
                    result.getMinimumSuccessPercent(),
                    result.getCustomersBelowRequiredSuccess(),
                    result.getExpectedFailureCost(),
                    (double) result.getRuntimeMillis()));
        }
        rewriteExperimentLogQuietly();
    }

    /** Records an unexpected run error and lets the remaining runs continue. */
    public void recordRunException(Throwable error) {
        if (error == null) {
            return;
        }
        line("");
        line("Run exception diagnostic:");
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        String[] lines = buffer.toString().split("\\R");
        for (String stackLine : lines) {
            line("  " + stackLine);
        }
    }

    public void completeRun(RunResult result, SearchEngine searchEngine) {
        appendResult(result);
        appendPostSearchDiagnostics(result, searchEngine);
        appendFinalExactSchedulingDiagnostics(result, searchEngine);
        if (result.isSuccess()) {
            writeRunSolution(result, searchEngine);
            maybeWriteBestSolution(result, searchEngine);
            line("");
            line("sas-alns run finished.");
            if (ExperimentParameters.usesRobustDeterministicTimes()) {
                line(String.format(Locale.US, "  Robust planning objective: %.6f", result.getObjective()));
                line(String.format(Locale.US, "  Robust-basis operating cost: %.6f", result.getDeterministicObjective()));
            } else {
                line(String.format(Locale.US, "  Final objective: %.6f", result.getObjective()));
            }
            line("  Best SA/LNS iteration: "
                    + simulatedAnnealingCycleFor(result.getBestIteration()) + "/"
                    + largeNeighborhoodSearchIterationFor(result.getBestIteration())
                    + " (total " + result.getBestIteration() + ")");
            line("  Time to best: " + formatDuration(result.getTimeToBestSeconds()));
            line("  Total runtime: " + formatDuration(result.getRuntimeSeconds()));
            line(String.format(Locale.US, "  Events/FEVs/SEVs/waiting: %d/%d/%d/%.1f",
                    result.getMeetingEventCount(), result.getFirstEchelonVehicleCount(), result.getSecondEchelonVehicleCount(), result.getTotalWaitingTime()));
        } else {
            line("");
            line("sas-alns run failed.");
            line("  Reason: " + result.getMessage());
            if (result.hasSearchMetrics()) {
                line(String.format(Locale.US, "  Completed-search objective: %.6f", result.getObjective()));
                line("  Best SA/LNS iteration: "
                        + simulatedAnnealingCycleFor(result.getBestIteration()) + "/"
                        + largeNeighborhoodSearchIterationFor(result.getBestIteration())
                        + " (total " + result.getBestIteration() + ")");
                line("  Time to best: " + formatDuration(result.getTimeToBestSeconds()));
            }
            line("  Runtime: " + formatDuration(result.getRuntimeSeconds()));
        }

        line("");
        writeInstanceRunTableToConsole(result.getInstance());

        isRunActive = false;
        rewriteExperimentLogQuietly();
    }

    public void completeInstance(String instance) {
        // The current instance table is printed after every completed run.
        // Therefore the final run already prints the FINAL instance table, and
        // repeating it here would duplicate the same table in batch-console.log.
        rewriteExperimentLogQuietly();
    }

    public void completeExperiment() {
        isExperimentComplete = true;
        line("");
        line("sas-alns batch finished.");
        line("Runs completed: " + totalResults());
        line("Run status: success=" + successfulResultCount()
                + " | failed=" + (totalResults() - successfulResultCount()));
        if (!ProblemParameters.isDellaert() && ExperimentParameters.validateFinalMonteCarlo) {
            line("Final validation: pass=" + finalValidationPassCount()
                    + " | fail=" + finalValidationFailCount()
                    + " | complete=" + finalValidationCompleteCount()
                    + " | error=" + finalValidationErrorCount()
                    + " | not-run=" + finalValidationNotRunCount());
        }
        line("Batch results: " + batchDirectory.toAbsolutePath());
        line("");
        writeCumulativeTableToConsole();
        line("");
        writeOverallSummaryToConsole();
        rewriteExperimentLogQuietly();
        flushConsoleQuietly();
    }

    private void writeBatchHeader() {
        line("============================================================");
        line("sas-alns MULTI-INSTANCE BATCH");
        line("============================================================");
        line("Batch label: " + ExperimentParameters.batchLabel);
        line("Execution environment: " + projectSettings.getExecutionEnvironment());
        line("Problem: " + projectSettings.getSelectedProblemVariant().configName());
        line("Evaluation: " + evaluationLabel());
        line("Instance directory: " + projectSettings.getInstanceDirectory().toAbsolutePath());
        line("Selection: " + selectionDescription());
        line("Matched instances: " + expectedInstances.size());
        for (int i = 0; i < expectedInstances.size(); i++) {
            line(String.format(Locale.US, "  %02d. %s.txt", i + 1, expectedInstances.get(i)));
        }
        line("Runs per instance: " + runsPerInstance);
        line("Base seed: " + ExperimentParameters.searchSeedStart);
        line("Scheduling mode: " + ExperimentParameters.schedulingMode);
        line("Deterministic time basis: " + ExperimentParameters.deterministicTimeMode);
        if (ExperimentParameters.usesRobustDeterministicTimes()) {
            line(String.format(Locale.US, "Robust quantile of active truncated distribution: %.4f",
                    ExperimentParameters.robustQuantile));
            line(String.format(Locale.US, "Equivalent parent-lognormal quantile: %.4f",
                    ExperimentParameters.robustParentQuantile()));
        }
        line("Meeting-point accessibility: " + ExperimentParameters.meetingPointAccessibility);
        if ("OUTER_ZONE".equals(ExperimentParameters.meetingPointAccessibility)) {
            line(String.format(Locale.US, "Outer-zone radius: %.3f km | center=(%.3f, %.3f)",
                    ExperimentParameters.outerZoneRadiusKm, ExperimentParameters.cityCenterX, ExperimentParameters.cityCenterY));
        }
        line("Detailed scheduling diagnostics: "
                + (ExperimentParameters.schedulingDiagnosticsDetailed ? "ENABLED" : "DISABLED"));
        if (ExperimentParameters.validateFinalExactSchedule) {
            line("Scheduling diagnostics directory: " + diagnosticsDirectory.toAbsolutePath());
        }
        line("Console progress interval: " + checkpointInterval + " LNS iterations per SA cycle");
        line("Final lightweight validation: "
                + (ExperimentParameters.validateFinalSolution ? "ENABLED" : "DISABLED"));
        line("SA validation checkpoint interval: "
                + (ExperimentParameters.validationSaCheckpointInterval == 0
                        ? "DISABLED"
                        : ExperimentParameters.validationSaCheckpointInterval + " SA cycle(s)"));
        line("Batch directory: " + batchDirectory.toAbsolutePath());
        line("Live batch log: " + batchConsoleLog.toAbsolutePath());
        line("Experiment report: " + experimentLog.toAbsolutePath());
    }

    private void writeSearchHeader() {
        String header = ExperimentParameters.isStochasticEvaluation()
                ? STOCHASTIC_SEARCH_HEADER : DETERMINISTIC_SEARCH_HEADER;
        line(header);
        line(repeat('-', header.length()));
    }

    private void writeSearchRow(
            String phase,
            int simulatedAnnealingCycle,
            int largeNeighborhoodSearchIteration,
            Solution currentSolution,
            Solution bestSolution,
            double temperature,
            double feasibilityRate,
            double acceptanceRate,
            int stagnation,
            String event) {
        double improvementPercent = ReportStatistics.improvementPercent(currentInitialObjective, bestSolution.objective);
        String elapsedTime = formatDuration(currentRunElapsedSeconds());

        if (ExperimentParameters.isStochasticEvaluation()) {
            SuccessStats successStatistics = successStats(bestSolution);
            line(String.format(Locale.US,
                    "%-7s %3d %8d %10s %13.3f %13.3f %7.2f %13.3f %12.3f %7s %7s %5s %4d %4d %4d %9.1f %10.4f %7s %7s %7d %s",
                    phase,
                    simulatedAnnealingCycle,
                    largeNeighborhoodSearchIteration,
                    elapsedTime,
                    currentSolution.objective,
                    bestSolution.objective,
                    finiteOrZero(improvementPercent),
                    bestSolution.deterministicCost,
                    bestSolution.recourseCost,
                    rate(successStatistics.averageSuccessPercent),
                    rate(successStatistics.minimumSuccessPercent),
                    successStatistics.customersBelowRequiredSuccess < 0 ? "-" : Integer.toString(successStatistics.customersBelowRequiredSuccess),
                    bestSolution.getMeetingPointCount(),
                    bestSolution.getUsedFirstEchelonVehicleCount(),
                    bestSolution.getUsedSecondEchelonVehicleCount(),
                    bestSolution.getTotalWaitingTime(),
                    temperature,
                    rate(feasibilityRate),
                    rate(acceptanceRate),
                    stagnation,
                    event));
        } else {
            line(String.format(Locale.US,
                    "%-7s %3d %8d %10s %13.3f %13.3f %7.2f %4d %4d %4d %9.1f %10.4f %7s %7s %7d %s",
                    phase,
                    simulatedAnnealingCycle,
                    largeNeighborhoodSearchIteration,
                    elapsedTime,
                    currentSolution.objective,
                    bestSolution.objective,
                    finiteOrZero(improvementPercent),
                    bestSolution.getMeetingPointCount(),
                    bestSolution.getUsedFirstEchelonVehicleCount(),
                    bestSolution.getUsedSecondEchelonVehicleCount(),
                    bestSolution.getTotalWaitingTime(),
                    temperature,
                    rate(feasibilityRate),
                    rate(acceptanceRate),
                    stagnation,
                    event));
        }
    }

    private int simulatedAnnealingCycleFor(int totalIteration) {
        if (totalIteration <= 0) {
            return 0;
        }
        return ((totalIteration - 1) / ExperimentParameters.maxLnsIterations) + 1;
    }

    private int largeNeighborhoodSearchIterationFor(int totalIteration) {
        if (totalIteration <= 0) {
            return 0;
        }
        return ((totalIteration - 1) % ExperimentParameters.maxLnsIterations) + 1;
    }

    private void appendResult(RunResult result) {
        resultsByInstance.computeIfAbsent(result.getInstance(), key -> new ArrayList<>()).add(result);
        String row = String.join(",",
                csv(result.getInstance()),
                Integer.toString(result.getRunNumber()),
                Integer.toString(result.getSeed()),
                result.getStatus(),
                result.getFinalValidationStatus(),
                Boolean.toString(result.hasSearchMetrics()),
                number(result.getObjective()),
                number(result.getDeterministicObjective()),
                csv(ExperimentParameters.deterministicTimeMode),
                ExperimentParameters.isStochasticEvaluation() ? number(result.getRecourseCost()) : "",
                ExperimentParameters.isStochasticEvaluation() ? number(result.getAverageSuccessPercent()) : "",
                ExperimentParameters.isStochasticEvaluation() ? number(result.getMinimumSuccessPercent()) : "",
                ExperimentParameters.isStochasticEvaluation() && result.hasSearchMetrics()
                        ? Integer.toString(result.getCustomersBelowRequiredSuccess()) : "",
                result.getFinalValidationReplications() >= 0
                        ? Integer.toString(result.getFinalValidationReplications()) : "",
                number(result.getFinalValidationAverageSuccessPercent()),
                number(result.getFinalValidationMinimumSuccessPercent()),
                result.getFinalValidationCustomersBelowRequiredSuccess() >= 0
                        ? Integer.toString(result.getFinalValidationCustomersBelowRequiredSuccess()) : "",
                result.hasSearchMetrics() ? Integer.toString(result.getMeetingEventCount()) : "",
                result.hasSearchMetrics() ? Integer.toString(result.getFirstEchelonVehicleCount()) : "",
                result.hasSearchMetrics() ? Integer.toString(result.getSecondEchelonVehicleCount()) : "",
                number(result.getTotalWaitingTime()),
                number(result.getTotalDistance()),
                number(result.getInitialObjective()),
                number(result.getImprovementPercent()),
                result.hasSearchMetrics() ? Integer.toString(simulatedAnnealingCycleFor(result.getBestIteration())) : "",
                result.hasSearchMetrics() ? Integer.toString(largeNeighborhoodSearchIterationFor(result.getBestIteration())) : "",
                result.hasSearchMetrics() ? Integer.toString(result.getBestIteration()) : "",
                number(result.getTimeToBestSeconds()),
                ExperimentParameters.isStochasticEvaluation() ? number(result.getStochasticEvaluationTimeSeconds()) : "",
                ExperimentParameters.isStochasticEvaluation() ? number(result.getStochasticRuntimePercent()) : "",
                number(result.getRuntimeSeconds()),
                csv(result.getFinalValidationMessage()),
                csv(result.getMessage()));
        try {
            Files.writeString(runsCsv, row + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot append run result to " + runsCsv, exception);
        }
    }

    private void appendPostSearchDiagnostics(
            RunResult result,
            SearchEngine searchEngine) {
        ExactScheduleResult exact = searchEngine == null
                ? null : searchEngine.finalExactVerificationResult;
        ExactScheduleResult freeExact = searchEngine == null
                ? null : searchEngine.finalFreeExactDiagnosticResult;
        boolean freeExactRan = freeExact != null && !"NOT_RUN".equals(freeExact.getMessage());
        ScheduleSnapshot exactSchedule = exact == null ? null : exact.getScheduleSnapshot();
        ScheduleSnapshot freeExactSchedule = freeExactRan ? freeExact.getScheduleSnapshot() : null;
        StochasticMonteCarloResult mc = searchEngine == null
                ? null : searchEngine.finalMonteCarloValidationResult;

        String row = String.join(",",
                csv(result.getInstance()),
                Integer.toString(result.getRunNumber()),
                Integer.toString(result.getSeed()),
                Boolean.toString(ExperimentParameters.validateFinalExactSchedule),
                exact == null ? "" : Boolean.toString(exact.isCompletedSuccessfully()),
                exact == null || !exact.hasFeasibilityCertificate()
                        ? "" : Boolean.toString(exact.isCertifiedFeasible()),
                exact == null ? "" : Boolean.toString(exact.usesFixedDepartures()),
                exact == null ? "" : Integer.toString(exact.getSolverStatus()),
                exact == null ? "" : number(exact.getObjective()),
                exactSchedule == null ? "" : number(exactSchedule.getDeterministicOperatingCost()),
                exactSchedule == null ? "" : number(exactSchedule.getTotalWaitingTime()),
                exactSchedule == null ? "" : number(exactSchedule.getTotalWaitingCost()),
                exact == null ? "" : Long.toString(exact.getRuntimeMillis()),
                exact == null ? "" : csv(exact.getMessage()),
                !freeExactRan ? "" : Boolean.toString(freeExact.isCompletedSuccessfully()),
                !freeExactRan || !freeExact.hasFeasibilityCertificate()
                        ? "" : Boolean.toString(freeExact.isCertifiedFeasible()),
                !freeExactRan ? "" : Integer.toString(freeExact.getSolverStatus()),
                freeExactSchedule == null ? "" : number(freeExactSchedule.getDeterministicOperatingCost()),
                freeExactSchedule == null ? "" : number(freeExactSchedule.getTotalWaitingTime()),
                freeExactSchedule == null ? "" : number(freeExactSchedule.getTotalWaitingCost()),
                !freeExactRan ? "" : Long.toString(freeExact.getRuntimeMillis()),
                !freeExactRan ? "" : csv(freeExact.getMessage()),
                Boolean.toString(ExperimentParameters.isStochasticEvaluation()
                        && ExperimentParameters.validateFinalMonteCarlo),
                mc == null ? "" : Boolean.toString(mc.isCompletedSuccessfully()),
                result.getFinalValidationStatus(),
                mc == null ? "" : Integer.toString(mc.getReplications()),
                mc == null ? "" : number(mc.getExpectedFailureCost()),
                mc == null ? "" : number(mc.getAverageSuccessPercent()),
                mc == null ? "" : number(mc.getMinimumSuccessPercent()),
                mc == null || !mc.isCompletedSuccessfully()
                        ? "" : Integer.toString(mc.getCustomersBelowRequiredSuccess()),
                mc == null ? "" : number(mc.getMeanAbsoluteFailureProbabilityError()),
                mc == null ? "" : number(mc.getMaximumAbsoluteFailureProbabilityError()),
                mc == null ? "" : Long.toString(mc.getRuntimeMillis()),
                mc == null ? "" : csv(mc.getMessage()));
        try {
            Files.writeString(
                    validationCsv,
                    row + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot append validation diagnostics to " + validationCsv,
                    exception);
        }
    }

    private void appendFinalExactSchedulingDiagnostics(
            RunResult result,
            SearchEngine searchEngine) {
        if (searchEngine == null || !ExperimentParameters.validateFinalExactSchedule) {
            return;
        }

        ScheduleSnapshot searchSchedule = searchEngine.finalSearchScheduleSnapshot;
        ExactScheduleResult primary = searchEngine.finalExactVerificationResult;
        ScheduleSnapshot primarySchedule = primary == null
                ? null : primary.getScheduleSnapshot();
        ExactScheduleResult free = searchEngine.finalFreeExactDiagnosticResult;
        boolean freeRan = free != null && !"NOT_RUN".equals(free.getMessage());
        ScheduleSnapshot freeSchedule = freeRan ? free.getScheduleSnapshot() : null;

        Solution searchSolution = searchEngine.finalSolution;
        Solution primarySolution = ExperimentParameters.schedulingDiagnosticsDetailed
                && primary != null ? primary.getVerifiedSolution() : null;
        Solution freeSolution = ExperimentParameters.schedulingDiagnosticsDetailed
                && freeRan ? free.getVerifiedSolution() : null;

        double searchPrimaryCostError = searchSchedule != null && primarySchedule != null
                ? Math.abs(searchSchedule.getDeterministicOperatingCost()
                        - primarySchedule.getDeterministicOperatingCost())
                : Double.NaN;
        double searchPrimaryWaitingError = searchSchedule != null && primarySchedule != null
                ? Math.abs(searchSchedule.getTotalWaitingTime()
                        - primarySchedule.getTotalWaitingTime())
                : Double.NaN;
        double primaryFreeCostGap = primarySchedule != null && freeSchedule != null
                ? primarySchedule.getDeterministicOperatingCost()
                        - freeSchedule.getDeterministicOperatingCost()
                : Double.NaN;
        double primaryFreeWaitingGap = primarySchedule != null && freeSchedule != null
                ? primarySchedule.getTotalWaitingTime()
                        - freeSchedule.getTotalWaitingTime()
                : Double.NaN;

        String row = String.join(",",
                csv(result.getInstance()),
                Integer.toString(result.getRunNumber()),
                Integer.toString(result.getSeed()),
                ExperimentParameters.isStochasticEvaluation() ? "STOCHASTIC" : "DETERMINISTIC",
                exactDiagnosticClassification(primary, freeRan ? free : null),
                searchSchedule == null ? "" : number(searchSchedule.getDeterministicOperatingCost()),
                searchSchedule == null ? "" : number(searchSchedule.getTotalWaitingTime()),
                searchSchedule == null ? "" : number(searchSchedule.getTotalWaitingCost()),
                primary == null ? "" : Boolean.toString(primary.usesFixedDepartures()),
                primary == null ? "" : Boolean.toString(primary.isCompletedSuccessfully()),
                primary == null || !primary.hasFeasibilityCertificate()
                        ? "" : Boolean.toString(primary.isCertifiedFeasible()),
                primary == null ? "" : Integer.toString(primary.getSolverStatus()),
                primarySchedule == null ? "" : number(primarySchedule.getDeterministicOperatingCost()),
                primarySchedule == null ? "" : number(primarySchedule.getTotalWaitingTime()),
                primarySchedule == null ? "" : number(primarySchedule.getTotalWaitingCost()),
                primary == null ? "" : Long.toString(primary.getRuntimeMillis()),
                primary == null ? "" : csv(primary.getMessage()),
                number(searchPrimaryCostError),
                number(searchPrimaryWaitingError),
                !freeRan ? "" : Boolean.toString(free.isCompletedSuccessfully()),
                !freeRan || !free.hasFeasibilityCertificate()
                        ? "" : Boolean.toString(free.isCertifiedFeasible()),
                !freeRan ? "" : Integer.toString(free.getSolverStatus()),
                freeSchedule == null ? "" : number(freeSchedule.getDeterministicOperatingCost()),
                freeSchedule == null ? "" : number(freeSchedule.getTotalWaitingTime()),
                freeSchedule == null ? "" : number(freeSchedule.getTotalWaitingCost()),
                !freeRan ? "" : Long.toString(free.getRuntimeMillis()),
                !freeRan ? "" : csv(free.getMessage()),
                number(primaryFreeCostGap),
                number(primaryFreeWaitingGap),
                stochasticMetric(searchSolution, StochasticMetric.OBJECTIVE),
                stochasticMetric(searchSolution, StochasticMetric.FAILURE_COST),
                stochasticMetric(searchSolution, StochasticMetric.AVG_SUCCESS),
                stochasticMetric(searchSolution, StochasticMetric.MIN_SUCCESS),
                stochasticMetric(searchSolution, StochasticMetric.BELOW_REQUIRED),
                stochasticMetric(primarySolution, StochasticMetric.OBJECTIVE),
                stochasticMetric(primarySolution, StochasticMetric.FAILURE_COST),
                stochasticMetric(primarySolution, StochasticMetric.AVG_SUCCESS),
                stochasticMetric(primarySolution, StochasticMetric.MIN_SUCCESS),
                stochasticMetric(primarySolution, StochasticMetric.BELOW_REQUIRED),
                stochasticMetric(freeSolution, StochasticMetric.OBJECTIVE),
                stochasticMetric(freeSolution, StochasticMetric.FAILURE_COST),
                stochasticMetric(freeSolution, StochasticMetric.AVG_SUCCESS),
                stochasticMetric(freeSolution, StochasticMetric.MIN_SUCCESS),
                stochasticMetric(freeSolution, StochasticMetric.BELOW_REQUIRED));

        appendCsvRow(finalExactDiagnosticsCsv, row, "final exact scheduling diagnostics");

        if (ExperimentParameters.schedulingDiagnosticsDetailed) {
            if (searchSchedule != null && primarySchedule != null) {
                appendScheduleDetails("FINAL_SEARCH_VS_PRIMARY", 0, searchSchedule, primarySchedule);
            }
            if (searchSchedule != null && freeSchedule != null) {
                appendScheduleDetails("FINAL_SEARCH_VS_FREE", 0, searchSchedule, freeSchedule);
            }
            if (primarySchedule != null && freeSchedule != null) {
                appendScheduleDetails("FINAL_PRIMARY_VS_FREE", 0, primarySchedule, freeSchedule);
            }
        }
    }

    private static String exactDiagnosticClassification(
            ExactScheduleResult primary,
            ExactScheduleResult free) {
        if (primary == null) {
            return "NO_PRIMARY_RESULT";
        }
        if (!primary.isCompletedSuccessfully()) {
            return "PRIMARY_OPERATIONAL_FAILURE";
        }
        if (!primary.hasFeasibilityCertificate()) {
            return "PRIMARY_NO_FEASIBILITY_CERTIFICATE";
        }
        if (primary.isCertifiedFeasible()) {
            if (free == null) {
                return primary.usesFixedDepartures()
                        ? "FIXED_DEPARTURES_CERTIFIED"
                        : "EXACT_CERTIFIED";
            }
            if (!free.isCompletedSuccessfully()) {
                return "FIXED_CERTIFIED_FREE_COMPARISON_FAILED";
            }
            if (free.isCertifiedFeasible()) {
                return "FIXED_AND_FREE_CERTIFIED";
            }
            return "FIXED_CERTIFIED_FREE_NOT_CERTIFIED";
        }
        if (free == null) {
            return primary.usesFixedDepartures()
                    ? "FIXED_DEPARTURES_NOT_CERTIFIED"
                    : "EXACT_NOT_CERTIFIED";
        }
        if (!free.isCompletedSuccessfully()) {
            return "FIXED_NOT_CERTIFIED_FREE_COMPARISON_FAILED";
        }
        if (free.isCertifiedFeasible()) {
            return "FIXED_DEPARTURES_CAUSE_INFEASIBILITY";
        }
        return "ROUTE_STRUCTURE_NOT_EXACTLY_SCHEDULABLE";
    }

    private enum StochasticMetric { OBJECTIVE, FAILURE_COST, AVG_SUCCESS, MIN_SUCCESS, BELOW_REQUIRED }

    private static String stochasticMetric(Solution solution, StochasticMetric metric) {
        if (!ExperimentParameters.isStochasticEvaluation() || solution == null) {
            return "";
        }
        switch (metric) {
            case OBJECTIVE:
                return number(solution.objective);
            case FAILURE_COST:
                return number(solution.recourseCost);
            case AVG_SUCCESS:
                return number(averageSuccessPercent(solution));
            case MIN_SUCCESS:
                return number(minimumSuccessPercent(solution));
            case BELOW_REQUIRED:
                return Integer.toString(customersBelowRequiredSuccess(solution));
            default:
                return "";
        }
    }

    private void appendScheduleDetails(
            String context,
            int sequence,
            ScheduleSnapshot approximate,
            ScheduleSnapshot exact) {
        if (approximate == null || exact == null) {
            return;
        }

        for (ScheduleSnapshot.RouteSnapshot approximateRoute : approximate.getRoutes()) {
            ScheduleSnapshot.RouteSnapshot exactRoute = exact.findRoute(
                    approximateRoute.getEchelon(),
                    approximateRoute.getVehicleId());
            if (exactRoute == null) {
                continue;
            }

            ScheduleSnapshot.VisitSnapshot approximateFirst = approximateRoute.getVisits().isEmpty()
                    ? null : approximateRoute.getVisits().get(0);
            ScheduleSnapshot.VisitSnapshot exactFirst = exactRoute.getVisits().isEmpty()
                    ? null : exactRoute.getVisits().get(0);

            String vehicleRow = String.join(",",
                    csv(currentInstance), Integer.toString(currentRun), Integer.toString(currentSeed),
                    context, Integer.toString(sequence), approximateRoute.getEchelon(),
                    Integer.toString(approximateRoute.getVehicleId()), csv(approximateRoute.getOriginId()),
                    number(approximateRoute.getDepartureTime()), number(exactRoute.getDepartureTime()),
                    number(approximateRoute.getDepartureTime() - exactRoute.getDepartureTime()),
                    number(approximateRoute.getTotalWaitingTime()), number(exactRoute.getTotalWaitingTime()),
                    number(approximateRoute.getTotalWaitingTime() - exactRoute.getTotalWaitingTime()),
                    number(approximateRoute.getTotalWaitingCost()), number(exactRoute.getTotalWaitingCost()),
                    number(approximateRoute.getTotalWaitingCost() - exactRoute.getTotalWaitingCost()),
                    approximateFirst == null ? "" : csv(approximateFirst.getNodeId()),
                    approximateFirst == null ? "" : number(approximateFirst.getLowerBound()),
                    approximateFirst == null ? "" : number(approximateFirst.getUpperBound()),
                    approximateFirst == null ? "" : number(approximateFirst.getVisitTime()),
                    exactFirst == null ? "" : number(exactFirst.getVisitTime()),
                    approximateFirst == null || exactFirst == null ? ""
                            : number(approximateFirst.getVisitTime() - exactFirst.getVisitTime()));
            appendCsvRow(schedulingVehicleDetailsCsv, vehicleRow, "scheduling vehicle diagnostics");

            int visitCount = Math.min(
                    approximateRoute.getVisits().size(),
                    exactRoute.getVisits().size());
            for (int position = 0; position < visitCount; position++) {
                ScheduleSnapshot.VisitSnapshot a = approximateRoute.getVisits().get(position);
                ScheduleSnapshot.VisitSnapshot e = exactRoute.getVisits().get(position);
                String nodeRow = String.join(",",
                        csv(currentInstance), Integer.toString(currentRun), Integer.toString(currentSeed),
                        context, Integer.toString(sequence), approximateRoute.getEchelon(),
                        Integer.toString(approximateRoute.getVehicleId()), Integer.toString(position),
                        csv(a.getNodeId()), csv(e.getNodeId()), csv(a.getNodeType()),
                        number(a.getReadyTime()), number(a.getDueTime()),
                        number(a.getLowerBound()), number(a.getUpperBound()),
                        number(a.getArrivalTime()), number(e.getArrivalTime()),
                        number(a.getArrivalTime() - e.getArrivalTime()),
                        number(a.getVisitTime()), number(e.getVisitTime()),
                        number(a.getVisitTime() - e.getVisitTime()),
                        number(a.getWaitingTime()), number(e.getWaitingTime()),
                        number(a.getWaitingTime() - e.getWaitingTime()));
                appendCsvRow(schedulingNodeDetailsCsv, nodeRow, "scheduling node diagnostics");
            }

            int arcCount = Math.min(
                    approximateRoute.getArcs().size(),
                    exactRoute.getArcs().size());
            for (int position = 0; position < arcCount; position++) {
                ScheduleSnapshot.ArcSnapshot a = approximateRoute.getArcs().get(position);
                ScheduleSnapshot.ArcSnapshot e = exactRoute.getArcs().get(position);
                String arcRow = String.join(",",
                        csv(currentInstance), Integer.toString(currentRun), Integer.toString(currentSeed),
                        context, Integer.toString(sequence), approximateRoute.getEchelon(),
                        Integer.toString(approximateRoute.getVehicleId()), Integer.toString(position),
                        csv(a.getFromId()), csv(a.getToId()), csv(e.getFromId()), csv(e.getToId()),
                        number(a.getTravelTime()), number(a.getServiceTimeFrom()),
                        number(a.getFromVisitTime()), number(e.getFromVisitTime()),
                        number(a.getFromVisitTime() - e.getFromVisitTime()),
                        number(a.getArrivalTime()), number(e.getArrivalTime()),
                        number(a.getArrivalTime() - e.getArrivalTime()),
                        number(a.getVisitTime()), number(e.getVisitTime()),
                        number(a.getVisitTime() - e.getVisitTime()),
                        number(a.getWaitingTime()), number(e.getWaitingTime()),
                        number(a.getWaitingTime() - e.getWaitingTime()),
                        number(a.getWaitingCost()), number(e.getWaitingCost()),
                        number(a.getWaitingCost() - e.getWaitingCost()));
                appendCsvRow(schedulingArcDetailsCsv, arcRow, "scheduling arc diagnostics");
            }
        }
    }

    private static void appendCsvRow(Path path, String row, String label) {
        try {
            Files.writeString(
                    path,
                    row + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot append " + label + " to " + path, exception);
        }
    }

    private void writeRunSolution(RunResult result, SearchEngine searchEngine) {
        Path instanceDirectory = runSolutionsDirectory.resolve(result.getInstance());
        try {
            Files.createDirectories(instanceDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create run-solution directory " + instanceDirectory, exception);
        }

        Path runSolutionFile = instanceDirectory.resolve(String.format(
                Locale.US,
                "run-%02d-seed-%d.txt",
                result.getRunNumber(),
                result.getSeed()));
        writeSolutionReport(runSolutionFile, "sas-alns RUN SOLUTION", result, searchEngine);
    }

    private void maybeWriteBestSolution(RunResult result, SearchEngine searchEngine) {
        Double previousBestObjective = bestObjectiveByInstance.get(result.getInstance());
        if (previousBestObjective != null && result.getObjective() >= previousBestObjective) {
            return;
        }
        bestObjectiveByInstance.put(result.getInstance(), result.getObjective());

        Path bestSolutionFile = bestSolutionsDirectory.resolve(result.getInstance() + "-best.txt");
        writeSolutionReport(bestSolutionFile, "sas-alns BEST SOLUTION", result, searchEngine);
    }

    private void writeSolutionReport(
            Path solutionFile,
            String reportHeading,
            RunResult result,
            SearchEngine searchEngine) {
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append(reportHeading).append('\n')
           .append("=".repeat(reportHeading.length())).append('\n')
           .append("Instance: ").append(result.getInstance()).append('\n')
           .append("Problem: ").append(projectSettings.getSelectedProblemVariant().configName()).append('\n')
           .append("Evaluation: ").append(evaluationLabel()).append('\n')
           .append("Run/seed: ").append(result.getRunNumber()).append('/').append(result.getSeed()).append('\n')
           .append("Run status: ").append(result.getStatus()).append('\n')
           .append("Final validation status: ").append(result.getFinalValidationStatus()).append('\n')
           .append("Final lightweight validation: ")
           .append(ExperimentParameters.validateFinalSolution ? "PASSED" : "DISABLED")
           .append("\n\n")
           .append(String.format(Locale.US, "%s: %.6f%n",
                   ExperimentParameters.usesRobustDeterministicTimes()
                           ? "Robust planning objective" : "Optimization objective",
                   result.getObjective()))
           .append(String.format(Locale.US, "%s: %.6f%n",
                   ExperimentParameters.usesRobustDeterministicTimes()
                           ? "Robust-basis operating cost" : "Deterministic operating cost",
                   result.getDeterministicObjective()))
           .append("Deterministic time basis: ").append(ExperimentParameters.deterministicTimeMode).append('\n');

        if (ExperimentParameters.isStochasticEvaluation()) {
            reportBuilder.append(String.format(Locale.US, "Failure cost: %.6f%n", result.getRecourseCost()))
               .append(String.format(Locale.US, "Average success: %.2f%%%n", result.getAverageSuccessPercent()))
               .append(String.format(Locale.US, "Minimum success: %.2f%%%n", result.getMinimumSuccessPercent()))
               .append("Customers below required success: ").append(result.getCustomersBelowRequiredSuccess()).append('\n');
        }

        reportBuilder.append("\nVERIFICATION\n")
           .append("------------\n");
        if (searchEngine.finalExactVerificationResult != null) {
            ExactScheduleResult exact = searchEngine.finalExactVerificationResult;
            reportBuilder.append("Exact verification completed: ")
               .append(exact.isCompletedSuccessfully()).append('\n')
               .append("Exact certified feasible: ")
               .append(exact.hasFeasibilityCertificate()
                       ? Boolean.toString(exact.isCertifiedFeasible()) : "N/A")
               .append('\n')
               .append("Exact fixed departures: ").append(exact.usesFixedDepartures()).append('\n')
               .append("Exact message: ").append(exact.getMessage()).append('\n');
        }
        if (searchEngine.finalMonteCarloValidationResult != null) {
            StochasticMonteCarloResult mc = searchEngine.finalMonteCarloValidationResult;
            reportBuilder.append("Final MC completed: ").append(mc.isCompletedSuccessfully()).append('\n')
               .append("Final MC message: ").append(mc.getMessage()).append('\n');
            if (mc.isCompletedSuccessfully()) {
                reportBuilder.append("Final MC status: ").append(result.getFinalValidationStatus()).append('\n')
                   .append("Final MC replications: ").append(mc.getReplications()).append('\n')
                   .append(String.format(Locale.US, "Final MC average success: %.2f%%%n", mc.getAverageSuccessPercent()))
                   .append(String.format(Locale.US, "Final MC minimum success: %.2f%%%n", mc.getMinimumSuccessPercent()))
                   .append("Final MC customers below required success: ").append(mc.getCustomersBelowRequiredSuccess()).append('\n')
                   .append(String.format(Locale.US, "Final MC failure cost: %.6f%n", mc.getExpectedFailureCost()));
            }
        }
        reportBuilder.append("\nSEARCH\n")
           .append("------\n")
           .append(String.format(Locale.US, "Initial objective: %.6f%n", result.getInitialObjective()))
           .append(String.format(Locale.US, "Improvement: %.3f%%%n", result.getImprovementPercent()))
           .append("Best SA cycle: ").append(simulatedAnnealingCycleFor(result.getBestIteration())).append('\n')
           .append("Best LNS iteration: ").append(largeNeighborhoodSearchIterationFor(result.getBestIteration())).append('\n')
           .append("Best total iteration: ").append(result.getBestIteration()).append('\n')
           .append("Time to best: ").append(formatDuration(result.getTimeToBestSeconds())).append('\n')
           .append("Runtime: ").append(formatDuration(result.getRuntimeSeconds())).append("\n\n")
           .append("STRUCTURE\n")
           .append("---------\n")
           .append("Events: ").append(result.getMeetingEventCount()).append('\n')
           .append("Used FEVs: ").append(result.getFirstEchelonVehicleCount()).append('\n')
           .append("Used SEVs: ").append(result.getSecondEchelonVehicleCount()).append('\n')
           .append(String.format(Locale.US, "Total distance: %.3f%n", result.getTotalDistance()))
           .append(String.format(Locale.US, "Total waiting time: %.3f%n%n", result.getTotalWaitingTime()))
           .append(searchEngine.finalSolution.toCompactCostSummary())
           .append("\nFIRST-ECHELON ROUTES\n")
           .append("--------------------\n")
           .append(searchEngine.finalSolution.firstEchelon.toStrRoutesNewReport())
           .append("\nSECOND-ECHELON ROUTES\n")
           .append("---------------------\n")
           .append(searchEngine.finalSolution.secondEchelon.toStrRoutesNewReport());

        try {
            Files.writeString(solutionFile, reportBuilder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write solution report " + solutionFile, exception);
        }
    }

    private void writeInstanceRunTableToConsole(String instance) {
        List<RunResult> results = resultsByInstance.getOrDefault(instance, List.of());
        line("sas-alns INSTANCE RUN TABLE | " + instance);
        line("Status: " + (results.size() == runsPerInstance ? "FINAL" : "RUNNING")
                + " | completed=" + results.size() + "/" + runsPerInstance
                + " | run-success=" + countSuccessful(results)
                + " | run-failed=" + (results.size() - countSuccessful(results))
                + finalValidationSummary(results));
        writeRunTableLines(results, this::line);
    }

    private void writeCumulativeTableToConsole() {
        line("sas-alns CUMULATIVE MULTI-INSTANCE RUN TABLE");
        line("Status: " + (isExperimentComplete ? "FINAL" : "RUNNING")
                + " | instances=" + expectedInstances.size()
                + " | stochastic reporting="
                + (ExperimentParameters.isStochasticEvaluation() ? "ENABLED" : "DISABLED"));
        for (String instance : expectedInstances) {
            line("");
            List<RunResult> results = resultsByInstance.getOrDefault(instance, List.of());
            line("INSTANCE | " + instance
                    + " | completed=" + results.size() + "/" + runsPerInstance
                    + " | run-success=" + countSuccessful(results)
                    + " | run-failed=" + (results.size() - countSuccessful(results))
                    + finalValidationSummary(results));
            writeRunTableLines(results, this::line);
        }
    }

    private void writeOverallSummaryToConsole() {
        StringBuilder builder = new StringBuilder();
        appendOverallSummary(builder);
        for (String outputLine : builder.toString().split("\\R")) {
            if (!outputLine.isEmpty()) {
                line(outputLine);
            }
        }
    }

    private void rewriteExperimentLogQuietly() {
        try {
            rewriteExperimentLog();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot update live experiment log " + experimentLog, exception);
        }
    }

    private void rewriteExperimentLog() throws IOException {
        StringBuilder outputBuilder = new StringBuilder();
        outputBuilder.append("sas-alns OVERALL EXPERIMENT REPORT\n")
           .append("==================================\n")
           .append("Status: ").append(isExperimentComplete ? "FINAL" : "RUNNING").append('\n')
           .append("Execution mode: ").append(projectSettings.getExecutionEnvironment()).append('\n')
           .append("Problem: ").append(projectSettings.getSelectedProblemVariant().configName()).append('\n')
           .append("Evaluation: ").append(evaluationLabel()).append('\n')
           .append("Selection: ").append(selectionDescription()).append('\n')
           .append("Selected instances: ").append(expectedInstances.size()).append('\n')
           .append("Available instance sections: ").append(instanceDefinitions.size()).append('\n')
           .append("Instance failures: ").append(countInstanceFailures()).append('\n')
           .append("Elapsed experiment runtime: ")
           .append(formatDuration((System.currentTimeMillis() - experimentStartMillis) / 1000.0))
           .append("\n\n")
           .append("EXPERIMENT CONFIGURATION\n")
           .append("------------------------\n")
           .append("Exact input configuration (also copied as config.properties in this batch):\n\n")
           .append(configurationSnapshot)
           .append("\n\n")
           .append("INSTANCE DEFINITIONS\n")
           .append("--------------------\n");

        for (String instance : expectedInstances) {
            InstanceDefinition definition = instanceDefinitions.get(instance);
            outputBuilder.append(instance).append('\n');
            if (definition == null) {
                outputBuilder.append("  Status: not loaded yet\n");
            } else {
                definition.appendTo(outputBuilder);
            }
        }

        if (isRunActive) {
            outputBuilder.append("\nCURRENT RUN\n")
               .append("-----------\n")
               .append("Instance: ").append(currentInstance).append('\n')
               .append("Run/seed: ").append(currentRun).append('/').append(currentSeed).append('\n')
               .append("SA cycle: ").append(currentSaCycle).append('/').append(ExperimentParameters.maxSaIterations).append('\n')
               .append("LNS iteration: ").append(currentLnsIteration).append('/').append(ExperimentParameters.maxLnsIterations).append('\n')
               .append("Total LNS iteration: ").append(currentTotalIteration).append('/').append(totalIterationBudget).append('\n')
               .append("Runtime: ").append(formatDuration(currentRunElapsedSeconds())).append('\n')
               .append("Current objective: ").append(value(currentObjective)).append('\n')
               .append("Best objective: ").append(value(currentBestObjective)).append('\n')
               .append("Best SA/LNS iteration: ")
               .append(simulatedAnnealingCycleFor(currentBestIteration)).append('/')
               .append(largeNeighborhoodSearchIterationFor(currentBestIteration)).append('\n')
               .append("Best total iteration: ").append(currentBestIteration).append('\n')
               .append("Time to best: ").append(durationValue(currentBestElapsedSeconds)).append("\n");
        }

        outputBuilder.append("\n")
           .append(repeat('=', ExperimentParameters.isStochasticEvaluation() ? 232 : 145)).append('\n')
           .append("sas-alns CUMULATIVE MULTI-INSTANCE RUN TABLE\n")
           .append("Status: ").append(isExperimentComplete ? "FINAL" : "RUNNING")
           .append(" | available instance sections=").append(instanceDefinitions.size()).append('/')
           .append(expectedInstances.size())
           .append(" | instance failures=").append(countInstanceFailures())
           .append(" | stochastic reporting=")
           .append(ExperimentParameters.isStochasticEvaluation() ? "ENABLED" : "DISABLED")
           .append('\n')
           .append(repeat('=', ExperimentParameters.isStochasticEvaluation() ? 232 : 145)).append('\n');

        int instanceIndex = 0;
        for (String instance : expectedInstances) {
            instanceIndex++;
            List<RunResult> results = resultsByInstance.getOrDefault(instance, List.of());
            outputBuilder.append("INSTANCE ").append(instanceIndex).append('/').append(expectedInstances.size())
               .append(" | ").append(instance).append('\n')
               .append("Status: ").append(results.size() == runsPerInstance ? "FINAL" : "RUNNING")
               .append(" | completed=").append(results.size()).append('/').append(runsPerInstance)
               .append(" | run-success=").append(countSuccessful(results))
               .append(" | run-failed=").append(results.size() - countSuccessful(results))
               .append(finalValidationSummary(results))
               .append(" | evaluation=").append(evaluationLabel()).append('\n');
            appendRunTable(outputBuilder, results);
            outputBuilder.append('\n');
        }

        appendOverallSummary(outputBuilder);
        outputBuilder.append("\nNotes: RunStatus reports whether the computational run completed successfully. FinalValidation separately reports the independent final Monte Carlo check of the frozen final solution: PASS/FAIL for CCM, COMPLETE for PBM/deterministic/robust 3M runs, ERROR if validation could not be completed, and NOT_RUN when disabled or unavailable. Search AvgS/MinS/B<Req and validation VAvg/VMin/VB<Req are intentionally reported separately. Completed-search metrics remain in AVG/BEST/CV even if a later run-level check fails. Runs without numerical search results are excluded. CV uses the sample standard deviation (n-1). Stochastic time covers candidate evaluations during construction and search, not the separate final validation.\n");

        writeAtomically(experimentLog, outputBuilder.toString());
    }

    static void appendRunTable(StringBuilder outputBuilder, List<RunResult> results) {
        List<RunResult> metrics = withSearchMetrics(results);
        if (ExperimentParameters.isStochasticEvaluation()) {
            List<RunResult> validated = withCompletedFinalValidation(metrics);
            final int width = 232;
            outputBuilder.append(repeat('-', width)).append('\n');
            outputBuilder.append(String.format(Locale.US,
                    "%4s %10s %-9s %-15s %11s %11s %10s %7s %7s %7s %7s %7s %7s %4s %4s %4s %9s %9s %10s %10s %8s %10s%n",
                    "Run", "Seed", "RunStatus", "FinalValidation", "FinalObj", "DetCost", "FailCost",
                    "SAvg%", "SMin%", "SB<Req", "VAvg%", "VMin%", "VB<Req",
                    "Ev", "FEV", "SEV", "Waiting", "BestIter", "TimeBest", "StochTime", "Stoch%", "Runtime"));
            outputBuilder.append(repeat('-', width)).append('\n');
            for (RunResult result : results) {
                outputBuilder.append(String.format(Locale.US,
                        "%4d %10d %-9s %-15s %11s %11s %10s %7s %7s %7s %7s %7s %7s %4s %4s %4s %9s %9s %10s %10s %8s %10s%n",
                        result.getRunNumber(), result.getSeed(), result.getStatus(), result.getFinalValidationStatus(),
                        value(result.getObjective()), value(result.getDeterministicObjective()), value(result.getRecourseCost()),
                        percentPlain(result.getAverageSuccessPercent()), percentPlain(result.getMinimumSuccessPercent()),
                        result.hasSearchMetrics() ? Integer.toString(result.getCustomersBelowRequiredSuccess()) : "-",
                        percentPlain(result.getFinalValidationAverageSuccessPercent()),
                        percentPlain(result.getFinalValidationMinimumSuccessPercent()),
                        result.getFinalValidationCustomersBelowRequiredSuccess() >= 0
                                ? Integer.toString(result.getFinalValidationCustomersBelowRequiredSuccess()) : "-",
                        integerValue(result.getMeetingEventCount()), integerValue(result.getFirstEchelonVehicleCount()),
                        integerValue(result.getSecondEchelonVehicleCount()), value(result.getTotalWaitingTime()),
                        integerValue(result.getBestIteration()), durationValue(result.getTimeToBestSeconds()),
                        durationValue(result.getStochasticEvaluationTimeSeconds()), percentPlain(result.getStochasticRuntimePercent()),
                        durationValue(result.getRuntimeSeconds())));
            }
            if (!metrics.isEmpty()) {
                RunResult bestResult = best(metrics);
                outputBuilder.append(repeat('-', width)).append('\n');
                outputBuilder.append(String.format(Locale.US,
                        "%4s %10s %-9s %-15s %11s %11s %10s %7s %7s %7s %7s %7s %7s %4s %4s %4s %9s %9s %10s %10s %8s %10s%n",
                        "AVG", "-", metrics.size() + " REP", "-",
                        value(ReportStatistics.mean(metrics, RunResult::getObjective)),
                        value(ReportStatistics.mean(metrics, RunResult::getDeterministicObjective)),
                        value(ReportStatistics.mean(metrics, RunResult::getRecourseCost)),
                        percentPlain(ReportStatistics.mean(metrics, RunResult::getAverageSuccessPercent)),
                        percentPlain(ReportStatistics.mean(metrics, RunResult::getMinimumSuccessPercent)),
                        value(ReportStatistics.mean(metrics, r -> r.getCustomersBelowRequiredSuccess())),
                        validated.isEmpty() ? "-" : percentPlain(ReportStatistics.mean(validated, RunResult::getFinalValidationAverageSuccessPercent)),
                        validated.isEmpty() ? "-" : percentPlain(ReportStatistics.mean(validated, RunResult::getFinalValidationMinimumSuccessPercent)),
                        validated.isEmpty() ? "-" : value(ReportStatistics.mean(validated, r -> r.getFinalValidationCustomersBelowRequiredSuccess())),
                        value(ReportStatistics.mean(metrics, r -> r.getMeetingEventCount())),
                        value(ReportStatistics.mean(metrics, r -> r.getFirstEchelonVehicleCount())),
                        value(ReportStatistics.mean(metrics, r -> r.getSecondEchelonVehicleCount())),
                        value(ReportStatistics.mean(metrics, RunResult::getTotalWaitingTime)),
                        value(ReportStatistics.mean(metrics, r -> r.getBestIteration())),
                        durationValue(ReportStatistics.mean(metrics, RunResult::getTimeToBestSeconds)),
                        durationValue(ReportStatistics.mean(metrics, RunResult::getStochasticEvaluationTimeSeconds)),
                        percentPlain(ReportStatistics.stochasticRuntimeSharePercent(metrics)),
                        durationValue(ReportStatistics.mean(metrics, RunResult::getRuntimeSeconds))));
                outputBuilder.append(String.format(Locale.US,
                        "%4s %10d %-9s %-15s %11s %11s %10s %7s %7s %7d %7s %7s %7s %4d %4d %4d %9s %9d %10s %10s %8s %10s%n",
                        "BEST", bestResult.getSeed(), "RUN " + bestResult.getRunNumber(), bestResult.getFinalValidationStatus(),
                        value(bestResult.getObjective()), value(bestResult.getDeterministicObjective()), value(bestResult.getRecourseCost()),
                        percentPlain(bestResult.getAverageSuccessPercent()), percentPlain(bestResult.getMinimumSuccessPercent()),
                        bestResult.getCustomersBelowRequiredSuccess(),
                        percentPlain(bestResult.getFinalValidationAverageSuccessPercent()),
                        percentPlain(bestResult.getFinalValidationMinimumSuccessPercent()),
                        bestResult.getFinalValidationCustomersBelowRequiredSuccess() >= 0
                                ? Integer.toString(bestResult.getFinalValidationCustomersBelowRequiredSuccess()) : "-",
                        bestResult.getMeetingEventCount(), bestResult.getFirstEchelonVehicleCount(), bestResult.getSecondEchelonVehicleCount(),
                        value(bestResult.getTotalWaitingTime()), bestResult.getBestIteration(),
                        durationValue(bestResult.getTimeToBestSeconds()), durationValue(bestResult.getStochasticEvaluationTimeSeconds()),
                        percentPlain(bestResult.getStochasticRuntimePercent()), durationValue(bestResult.getRuntimeSeconds())));
                outputBuilder.append(repeat('-', width)).append('\n');
                outputBuilder.append("Final validation counts: PASS=").append(countFinalValidationStatus(results, "PASS"))
                   .append(" | FAIL=").append(countFinalValidationStatus(results, "FAIL"))
                   .append(" | COMPLETE=").append(countFinalValidationStatus(results, "COMPLETE"))
                   .append(" | ERROR=").append(countFinalValidationStatus(results, "ERROR"))
                   .append(" | NOT_RUN=").append(countFinalValidationStatus(results, "NOT_RUN")).append('\n');
                outputBuilder.append("CV: objective=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getObjective)))
                   .append(" | det.cost=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getDeterministicObjective)))
                   .append(" | exp.failure=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getRecourseCost)))
                   .append(" | search avg.success=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getAverageSuccessPercent)))
                   .append(" | stochastic time=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getStochasticEvaluationTimeSeconds)))
                   .append(" | runtime=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getRuntimeSeconds)))
                   .append(" | basis=").append(metrics.size()).append(" reported search-completed run(s)\n");
            }
        } else {
            final int width = 162;
            outputBuilder.append(repeat('-', width)).append('\n');
            outputBuilder.append(String.format(Locale.US,
                    "%4s %10s %-9s %-15s %12s %7s %5s %5s %10s %10s %10s %10s%n",
                    "Run", "Seed", "RunStatus", "FinalValidation", "Final Obj.", "Events", "FEV", "SEV",
                    "Waiting", "BestIter", "TimeBest", "Runtime"));
            outputBuilder.append(repeat('-', width)).append('\n');
            for (RunResult result : results) {
                outputBuilder.append(String.format(Locale.US,
                        "%4d %10d %-9s %-15s %12s %7s %5s %5s %10s %10s %10s %10s%n",
                        result.getRunNumber(), result.getSeed(), result.getStatus(), result.getFinalValidationStatus(),
                        value(result.getObjective()),
                        integerValue(result.getMeetingEventCount()), integerValue(result.getFirstEchelonVehicleCount()), integerValue(result.getSecondEchelonVehicleCount()),
                        value(result.getTotalWaitingTime()), integerValue(result.getBestIteration()),
                        durationValue(result.getTimeToBestSeconds()), durationValue(result.getRuntimeSeconds())));
            }
            if (!metrics.isEmpty()) {
                RunResult bestResult = best(metrics);
                outputBuilder.append(repeat('-', width)).append('\n');
                outputBuilder.append(String.format(Locale.US,
                        "%4s %10s %-9s %-15s %12s %7s %5s %5s %10s %10s %10s %10s%n",
                        "AVG", "-", metrics.size() + " REP", "-",
                        value(ReportStatistics.mean(metrics, RunResult::getObjective)),
                        value(ReportStatistics.mean(metrics, r -> r.getMeetingEventCount())),
                        value(ReportStatistics.mean(metrics, r -> r.getFirstEchelonVehicleCount())),
                        value(ReportStatistics.mean(metrics, r -> r.getSecondEchelonVehicleCount())),
                        value(ReportStatistics.mean(metrics, RunResult::getTotalWaitingTime)),
                        value(ReportStatistics.mean(metrics, r -> r.getBestIteration())),
                        durationValue(ReportStatistics.mean(metrics, RunResult::getTimeToBestSeconds)),
                        durationValue(ReportStatistics.mean(metrics, RunResult::getRuntimeSeconds))));
                outputBuilder.append(String.format(Locale.US,
                        "%4s %10d %-9s %-15s %12s %7d %5d %5d %10s %10d %10s %10s%n",
                        "BEST", bestResult.getSeed(), "RUN " + bestResult.getRunNumber(), bestResult.getFinalValidationStatus(),
                        value(bestResult.getObjective()),
                        bestResult.getMeetingEventCount(), bestResult.getFirstEchelonVehicleCount(), bestResult.getSecondEchelonVehicleCount(),
                        value(bestResult.getTotalWaitingTime()), bestResult.getBestIteration(),
                        durationValue(bestResult.getTimeToBestSeconds()), durationValue(bestResult.getRuntimeSeconds())));
                outputBuilder.append(repeat('-', width)).append('\n');
                outputBuilder.append("Final validation counts: PASS=").append(countFinalValidationStatus(results, "PASS"))
                   .append(" | FAIL=").append(countFinalValidationStatus(results, "FAIL"))
                   .append(" | COMPLETE=").append(countFinalValidationStatus(results, "COMPLETE"))
                   .append(" | ERROR=").append(countFinalValidationStatus(results, "ERROR"))
                   .append(" | NOT_RUN=").append(countFinalValidationStatus(results, "NOT_RUN")).append('\n');
                outputBuilder.append("CV: objective=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getObjective)))
                   .append(" | runtime=").append(percentValue(ReportStatistics.coefficientOfVariation(metrics, RunResult::getRuntimeSeconds)))
                   .append(" | basis=").append(metrics.size()).append(" reported search-completed run(s)\n");
            }
        }
    }

    private void appendOverallSummary(StringBuilder outputBuilder) {
        if (ExperimentParameters.isStochasticEvaluation()) {
            appendOverallStochasticSummary(outputBuilder);
        } else {
            appendOverallDeterministicSummary(outputBuilder);
        }
    }

    private void appendOverallDeterministicSummary(StringBuilder outputBuilder) {
        outputBuilder.append("\nOVERALL INSTANCE SUMMARY\n")
           .append(repeat('-', 170)).append('\n')
           .append(String.format(Locale.US,
                   "%-28s %4s %5s %12s %12s %8s %8s %7s %7s %10s %10s %10s %9s%n",
                   "Instance", "OK", "Fail", "Avg Obj.", "Best Obj.", "ObjCV%", "Events", "FEV", "SEV",
                   "Waiting", "BestIter", "AvgRun", "RunCV%"))
           .append(repeat('-', 170)).append('\n');

        List<InstanceSummary> summaries = new ArrayList<>();
        for (String instance : expectedInstances) {
            List<RunResult> all = resultsByInstance.getOrDefault(instance, List.of());
            List<RunResult> metrics = withSearchMetrics(all);
            InstanceSummary summary = InstanceSummary.from(instance, all, metrics);
            summaries.add(summary);
            outputBuilder.append(summary.deterministicRow());
        }
        outputBuilder.append(repeat('-', 170)).append('\n');
        if (!summaries.isEmpty()) {
            outputBuilder.append(InstanceSummary.overall(summaries).deterministicOverallRow());
        }
        outputBuilder.append(repeat('-', 170)).append('\n');
    }

    private void appendOverallStochasticSummary(StringBuilder outputBuilder) {
        final int summaryWidth = 252;
        outputBuilder.append("\nOVERALL STOCHASTIC COST, RELIABILITY, FINAL VALIDATION, AND RUNTIME SUMMARY\n")
           .append(repeat('-', summaryWidth)).append('\n')
           .append(String.format(Locale.US,
                   "%-28s %5s %7s %5s %5s %5s %5s %5s %11s %11s %8s %11s %10s %8s %9s %9s %9s %10s %8s %10s %8s%n",
                   "Instance", "RunOK", "RunFail", "VPass", "VFail", "VComp", "VErr", "VNR",
                   "AvgObj", "BestObj", "ObjCV%", "AvgDet", "AvgFail",
                   "SAvg%", "SWorst", "VWorst", "AvgB<Req", "AvgStoch", "Stoch%", "AvgRun", "RunCV%"))
           .append(repeat('-', summaryWidth)).append('\n');

        List<InstanceSummary> summaries = new ArrayList<>();
        for (String instance : expectedInstances) {
            List<RunResult> all = resultsByInstance.getOrDefault(instance, List.of());
            List<RunResult> metrics = withSearchMetrics(all);
            InstanceSummary summary = InstanceSummary.from(instance, all, metrics);
            summaries.add(summary);
            outputBuilder.append(summary.stochasticCostRow());
        }
        outputBuilder.append(repeat('-', summaryWidth)).append('\n');
        if (!summaries.isEmpty()) {
            outputBuilder.append(InstanceSummary.overall(summaries).stochasticCostOverallRow());
        }
        outputBuilder.append(repeat('-', summaryWidth)).append("\n\n")
           .append("OVERALL STRUCTURE SUMMARY\n")
           .append(repeat('-', 115)).append('\n')
           .append(String.format(Locale.US,
                   "%-28s %10s %8s %8s %12s %12s%n",
                   "Instance", "Events", "FEV", "SEV", "Waiting", "BestIter"))
           .append(repeat('-', 115)).append('\n');
        for (InstanceSummary summary : summaries) {
            outputBuilder.append(summary.structureRow());
        }
        outputBuilder.append(repeat('-', 115)).append('\n');
        if (!summaries.isEmpty()) {
            outputBuilder.append(InstanceSummary.overall(summaries).structureOverallRow());
        }
        outputBuilder.append(repeat('-', 115)).append('\n');
    }

    private void writeRunTableLines(List<RunResult> results, LineSink sink) {
        StringBuilder outputBuilder = new StringBuilder();
        appendRunTable(outputBuilder, results);
        String[] lines = outputBuilder.toString().split("\\R", -1);
        for (String value : lines) {
            if (!value.isEmpty()) {
                sink.accept(value);
            }
        }
    }

    private void writeAtomically(Path target, String content) throws IOException {
        Path temporaryFile = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporaryFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void line(String text) {
        try {
            consoleWriter.write(text);
            consoleWriter.newLine();
            consoleWriter.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write live batch console " + batchConsoleLog, exception);
        }
        System.out.println(text);
    }

    private void flushConsoleQuietly() {
        try {
            consoleWriter.flush();
        } catch (IOException ignored) {
            // close() reports a persistent write failure.
        }
    }

    private void buildExpectedInstanceNames() {
        for (int customerCount = ExperimentParameters.customerCountStart;
             customerCount <= ExperimentParameters.customerCountEnd;
             customerCount++) {
            for (int sampleIndex = ExperimentParameters.sampleIndexStart;
                 sampleIndex <= ExperimentParameters.sampleIndexEnd;
                 sampleIndex++) {
                expectedInstances.add(String.format(Locale.US,
                        "3M-Cb-%d-%d-%d-%d",
                        projectSettings.getSelectedDepotCount(),
                        projectSettings.getSelectedParkingCount(),
                        customerCount,
                        sampleIndex));
            }
        }
    }

    private String buildBatchDirectoryName() {
        String customerRange = ExperimentParameters.customerCountStart == ExperimentParameters.customerCountEnd
                ? Integer.toString(ExperimentParameters.customerCountStart)
                : ExperimentParameters.customerCountStart + "-" + ExperimentParameters.customerCountEnd;
        return ExperimentParameters.batchLabel
                + "_" + projectSettings.getSelectedDepotCount()
                + "_" + projectSettings.getSelectedParkingCount()
                + "_" + customerRange;
    }

    private static Path createUniqueBatchDirectory(Path parent, String baseName) throws IOException {
        Path candidate = parent.resolve(baseName);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + "-" + String.format(Locale.US, "%02d", suffix));
            suffix++;
        }
        Files.createDirectories(candidate);
        return candidate;
    }

    private String selectionDescription() {
        return String.format(Locale.US,
                "depots=%d, parkings=%d, customers=%s, samples=%s",
                projectSettings.getSelectedDepotCount(),
                projectSettings.getSelectedParkingCount(),
                range(ExperimentParameters.customerCountStart, ExperimentParameters.customerCountEnd),
                range(ExperimentParameters.sampleIndexStart, ExperimentParameters.sampleIndexEnd));
    }

    private static String range(int start, int end) {
        return start == end ? Integer.toString(start) : start + "-" + end;
    }

    private static String evaluationLabel() {
        if (ExperimentParameters.isDeterministicEvaluation()) {
            return "DETERMINISTIC";
        }
        return ExperimentParameters.stochasticMode.name() + "-" + ExperimentParameters.stochasticEvaluator;
    }

    private static SuccessStats successStats(Solution solution) {
        if (!ExperimentParameters.isStochasticEvaluation()
                || solution == null
                || solution.problem == null
                || solution.problem.customers == null
                || solution.problem.customers.customers.isEmpty()) {
            return SuccessStats.unavailable();
        }
        double totalSuccessPercent = 0.0;
        double minimumSuccessPercent = Double.POSITIVE_INFINITY;
        int customersBelowRequiredSuccess = 0;
        int customerCount = 0;
        for (Customer customer : solution.problem.customers.customers) {
            double successPercent = (1.0 - customer.secondEchelonFailureProbability) * 100.0;
            totalSuccessPercent += successPercent;
            minimumSuccessPercent = Math.min(minimumSuccessPercent, successPercent);
            if (customer.secondEchelonFailureProbability > ProblemParameters.failureProbability) {
                customersBelowRequiredSuccess++;
            }
            customerCount++;
        }
        if (customerCount == 0) {
            return SuccessStats.unavailable();
        }
        return new SuccessStats(totalSuccessPercent / customerCount, minimumSuccessPercent, customersBelowRequiredSuccess);
    }

    public static double averageSuccessPercent(Solution solution) {
        return successStats(solution).averageSuccessPercent;
    }

    public static double minimumSuccessPercent(Solution solution) {
        return successStats(solution).minimumSuccessPercent;
    }

    public static int customersBelowRequiredSuccess(Solution solution) {
        return successStats(solution).customersBelowRequiredSuccess;
    }

    private int totalResults() {
        return resultsByInstance.values().stream().mapToInt(List::size).sum();
    }

    private int successfulResultCount() {
        int successfulCount = 0;
        for (List<RunResult> results : resultsByInstance.values()) {
            successfulCount += countSuccessful(results);
        }
        return successfulCount;
    }

    private static int countSuccessful(List<RunResult> results) {
        int successfulCount = 0;
        for (RunResult result : results) {
            if (result.isSuccess()) {
                successfulCount++;
            }
        }
        return successfulCount;
    }


    private int finalValidationPassCount() {
        return countFinalValidationStatusAll("PASS");
    }

    private int finalValidationFailCount() {
        return countFinalValidationStatusAll("FAIL");
    }

    private int finalValidationCompleteCount() {
        return countFinalValidationStatusAll("COMPLETE");
    }

    private int finalValidationErrorCount() {
        return countFinalValidationStatusAll("ERROR");
    }

    private int finalValidationNotRunCount() {
        return countFinalValidationStatusAll("NOT_RUN");
    }

    private int countFinalValidationStatusAll(String status) {
        int count = 0;
        for (List<RunResult> results : resultsByInstance.values()) {
            count += countFinalValidationStatus(results, status);
        }
        return count;
    }

    private static int countFinalValidationStatus(List<RunResult> results, String status) {
        int count = 0;
        for (RunResult result : results) {
            if (status.equals(result.getFinalValidationStatus())) {
                count++;
            }
        }
        return count;
    }

    private static String finalValidationSummary(List<RunResult> results) {
        // Final Monte Carlo validation can be enabled for every 3M planning approach.
        // CCM yields PASS/FAIL; deterministic, robust and PBM runs yield COMPLETE.
        if (ProblemParameters.isDellaert()) {
            return "";
        }
        return " | final-validation-pass=" + countFinalValidationStatus(results, "PASS")
                + " | final-validation-fail=" + countFinalValidationStatus(results, "FAIL")
                + " | final-validation-complete=" + countFinalValidationStatus(results, "COMPLETE")
                + " | final-validation-error=" + countFinalValidationStatus(results, "ERROR")
                + " | final-validation-not-run=" + countFinalValidationStatus(results, "NOT_RUN");
    }

    private static List<RunResult> withCompletedFinalValidation(List<RunResult> results) {
        List<RunResult> validated = new ArrayList<>();
        for (RunResult result : results) {
            if (result.hasCompletedFinalValidation()) {
                validated.add(result);
            }
        }
        return validated;
    }
    private static List<RunResult> withSearchMetrics(List<RunResult> results) {
        List<RunResult> reported = new ArrayList<>();
        for (RunResult result : results) {
            if (result.hasSearchMetrics()) {
                reported.add(result);
            }
        }
        return reported;
    }

    private int countInstanceFailures() {
        if (!isExperimentComplete) {
            return 0;
        }
        return Math.max(0, expectedInstances.size() - instanceDefinitions.size());
    }

    private static RunResult best(List<RunResult> results) {
        RunResult bestResult = results.get(0);
        for (RunResult result : results) {
            if (result.getObjective() < bestResult.getObjective()) {
                bestResult = result;
            }
        }
        return bestResult;
    }

    private interface LineSink {
        void accept(String line);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static String rate(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.1f", value) : "-";
    }

    private static String percentPlain(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "-";
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.12f", value) : "";
    }

    private static String value(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.3f", value) : "-";
    }

    private static String integerValue(int value) {
        return value >= 0 ? Integer.toString(value) : "-";
    }

    private static String percentValue(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f%%", value) : "-";
    }

    private static String durationValue(double seconds) {
        return Double.isFinite(seconds) ? formatDuration(seconds) : "-";
    }

    public static String formatDuration(double durationSeconds) {
        if (!Double.isFinite(durationSeconds) || durationSeconds < 0) {
            return "-";
        }
        long millis = Math.round(durationSeconds * 1000.0);
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long wholeSeconds = (millis % 60_000) / 1000;
        long milliseconds = millis % 1000;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, wholeSeconds);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "%02d:%02d", minutes, wholeSeconds);
        }
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, wholeSeconds, milliseconds);
    }

    private static String csv(String text) {
        if (text == null) {
            return "";
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private static String shorten(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, Math.max(1, maximumLength - 1)) + "…";
    }

    private static String repeat(char character, int count) {
        StringBuilder builder = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            builder.append(character);
        }
        return builder.toString();
    }

    @Override
    public void close() throws IOException {
        if (isClosed) {
            return;
        }
        isClosed = true;
        consoleWriter.flush();
        consoleWriter.close();
    }

    private static final class InstanceDefinition {
        private final String distanceMetric;
        private final double distanceDivisor;
        private final int timeHorizonMinutes;
        private final double firstEchelonVehiclesPerDepot;
        private final double firstEchelonCapacityKg;
        private final double firstEchelonSpeedKmPerHour;
        private final double firstEchelonFixedCostEuroPerDay;
        private final double firstEchelonWageEuroPerHour;
        private final double firstEchelonFuelEuroPerKm;
        private final double secondEchelonVehiclesPerParking;
        private final double secondEchelonCapacityKg;
        private final double secondEchelonSpeedKmPerHour;
        private final double secondEchelonFixedCostEuroPerDay;
        private final double secondEchelonWageEuroPerHour;
        private final double secondEchelonFuelEuroPerKm;

        private InstanceDefinition(
                String distanceMetric,
                double distanceDivisor,
                int timeHorizonMinutes,
                double firstEchelonVehiclesPerDepot,
                double firstEchelonCapacityKg,
                double firstEchelonSpeedKmPerHour,
                double firstEchelonFixedCostEuroPerDay,
                double firstEchelonWageEuroPerHour,
                double firstEchelonFuelEuroPerKm,
                double secondEchelonVehiclesPerParking,
                double secondEchelonCapacityKg,
                double secondEchelonSpeedKmPerHour,
                double secondEchelonFixedCostEuroPerDay,
                double secondEchelonWageEuroPerHour,
                double secondEchelonFuelEuroPerKm) {
            this.distanceMetric = distanceMetric;
            this.distanceDivisor = distanceDivisor;
            this.timeHorizonMinutes = timeHorizonMinutes;
            this.firstEchelonVehiclesPerDepot = firstEchelonVehiclesPerDepot;
            this.firstEchelonCapacityKg = firstEchelonCapacityKg;
            this.firstEchelonSpeedKmPerHour = firstEchelonSpeedKmPerHour;
            this.firstEchelonFixedCostEuroPerDay = firstEchelonFixedCostEuroPerDay;
            this.firstEchelonWageEuroPerHour = firstEchelonWageEuroPerHour;
            this.firstEchelonFuelEuroPerKm = firstEchelonFuelEuroPerKm;
            this.secondEchelonVehiclesPerParking = secondEchelonVehiclesPerParking;
            this.secondEchelonCapacityKg = secondEchelonCapacityKg;
            this.secondEchelonSpeedKmPerHour = secondEchelonSpeedKmPerHour;
            this.secondEchelonFixedCostEuroPerDay = secondEchelonFixedCostEuroPerDay;
            this.secondEchelonWageEuroPerHour = secondEchelonWageEuroPerHour;
            this.secondEchelonFuelEuroPerKm = secondEchelonFuelEuroPerKm;
        }

        private static InstanceDefinition capture(ProblemInstance problem) {
            return new InstanceDefinition(
                    problem.getDistanceMetric().name(),
                    problem.getDistanceDivisor(),
                    ProblemParameters.timeHorizonMinutes,
                    ProblemParameters.firstEchelonVehiclesPerDepot,
                    ProblemParameters.firstEchelonVehicleCapacityKg,
                    ProblemParameters.firstEchelonVehicleSpeedKmPerMinute,
                    ProblemParameters.firstEchelonVehicleFixedCostEuro,
                    ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute,
                    ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm,
                    ProblemParameters.secondEchelonVehiclesPerParking,
                    ProblemParameters.secondEchelonVehicleCapacityKg,
                    ProblemParameters.secondEchelonVehicleSpeedKmPerMinute,
                    ProblemParameters.secondEchelonVehicleFixedCostEuro,
                    ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute,
                    ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm);
        }

        private void appendTo(StringBuilder builder) {
            builder.append("  Distance metric: ").append(distanceMetric).append('\n');
            if ("FLOOR_EUCLIDEAN_DIVISOR".equals(distanceMetric)) {
                builder.append(String.format(Locale.US, "  Distance divisor: %.6f%n", distanceDivisor));
            }
            builder.append("  Time horizon (min): ").append(timeHorizonMinutes).append('\n')
               .append(String.format(Locale.US,
                       "  FEVs/depot: %.0f | capacity: %.3f kg | speed: %.3f km/h | fixed: %.3f EUR/day | wage: %.3f EUR/h | fuel: %.6f EUR/km%n",
                       firstEchelonVehiclesPerDepot, firstEchelonCapacityKg, firstEchelonSpeedKmPerHour,
                       firstEchelonFixedCostEuroPerDay, firstEchelonWageEuroPerHour, firstEchelonFuelEuroPerKm))
               .append(String.format(Locale.US,
                       "  SEVs/parking: %.0f | capacity: %.3f kg | speed: %.3f km/h | fixed: %.3f EUR/day | wage: %.3f EUR/h | fuel: %.6f EUR/km%n",
                       secondEchelonVehiclesPerParking, secondEchelonCapacityKg, secondEchelonSpeedKmPerHour,
                       secondEchelonFixedCostEuroPerDay, secondEchelonWageEuroPerHour, secondEchelonFuelEuroPerKm));
        }
    }

    private static final class InstanceSummary {
        private final String instance;
        private final int successful;
        private final int failed;
        private final int finalValidationPassed;
        private final int finalValidationFailed;
        private final int finalValidationComplete;
        private final int finalValidationError;
        private final int finalValidationNotRun;
        private final double averageObjective;
        private final double bestObjective;
        private final double objectiveCv;
        private final double averageEvents;
        private final double averageFirstEchelonVehicles;
        private final double averageSecondEchelonVehicles;
        private final double averageWaiting;
        private final double averageBestIteration;
        private final double averageRuntime;
        private final double runtimeCv;
        private final double averageDeterministicCost;
        private final double averageFailureCost;
        private final double averageSuccessPercent;
        private final double worstMinimumSuccessPercent;
        private final double finalValidationWorstMinimumSuccessPercent;
        private final double averageBelowRequired;
        private final double averageStochasticTime;
        private final double averageStochasticRuntimePercent;

        private InstanceSummary(
                String instance, int successful, int failed,
                int finalValidationPassed, int finalValidationFailed, int finalValidationComplete,
                int finalValidationError, int finalValidationNotRun,
                double averageObjective, double bestObjective, double objectiveCv,
                double averageEvents, double averageFirstEchelonVehicles,
                double averageSecondEchelonVehicles, double averageWaiting,
                double averageBestIteration, double averageRuntime, double runtimeCv,
                double averageDeterministicCost,
                double averageFailureCost, double averageSuccessPercent, double worstMinimumSuccessPercent,
                double finalValidationWorstMinimumSuccessPercent,
                double averageBelowRequired, double averageStochasticTime,
                double averageStochasticRuntimePercent) {
            this.instance = instance;
            this.successful = successful;
            this.failed = failed;
            this.finalValidationPassed = finalValidationPassed;
            this.finalValidationFailed = finalValidationFailed;
            this.finalValidationComplete = finalValidationComplete;
            this.finalValidationError = finalValidationError;
            this.finalValidationNotRun = finalValidationNotRun;
            this.averageObjective = averageObjective;
            this.bestObjective = bestObjective;
            this.objectiveCv = objectiveCv;
            this.averageEvents = averageEvents;
            this.averageFirstEchelonVehicles = averageFirstEchelonVehicles;
            this.averageSecondEchelonVehicles = averageSecondEchelonVehicles;
            this.averageWaiting = averageWaiting;
            this.averageBestIteration = averageBestIteration;
            this.averageRuntime = averageRuntime;
            this.runtimeCv = runtimeCv;
            this.averageDeterministicCost = averageDeterministicCost;
            this.averageFailureCost = averageFailureCost;
            this.averageSuccessPercent = averageSuccessPercent;
            this.worstMinimumSuccessPercent = worstMinimumSuccessPercent;
            this.finalValidationWorstMinimumSuccessPercent = finalValidationWorstMinimumSuccessPercent;
            this.averageBelowRequired = averageBelowRequired;
            this.averageStochasticTime = averageStochasticTime;
            this.averageStochasticRuntimePercent = averageStochasticRuntimePercent;
        }

        private static InstanceSummary from(String instance, List<RunResult> all, List<RunResult> metrics) {
            int validationPass = countFinalValidationStatus(all, "PASS");
            int validationFail = countFinalValidationStatus(all, "FAIL");
            int validationComplete = countFinalValidationStatus(all, "COMPLETE");
            int validationError = countFinalValidationStatus(all, "ERROR");
            int validationNotRun = countFinalValidationStatus(all, "NOT_RUN");
            List<RunResult> validated = withCompletedFinalValidation(metrics);
            double validationWorstMinimum = validated.isEmpty()
                    ? Double.NaN
                    : ReportStatistics.min(validated, RunResult::getFinalValidationMinimumSuccessPercent);

            if (metrics.isEmpty()) {
                return new InstanceSummary(
                        instance, countSuccessful(all), all.size() - countSuccessful(all),
                        validationPass, validationFail, validationComplete, validationError, validationNotRun,
                        Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        validationWorstMinimum, Double.NaN, Double.NaN, Double.NaN);
            }
            return new InstanceSummary(
                    instance, countSuccessful(all), all.size() - countSuccessful(all),
                    validationPass, validationFail, validationComplete, validationError, validationNotRun,
                    ReportStatistics.mean(metrics, RunResult::getObjective), ReportStatistics.min(metrics, RunResult::getObjective),
                    ReportStatistics.coefficientOfVariation(metrics, RunResult::getObjective),
                    ReportStatistics.mean(metrics, r -> r.getMeetingEventCount()),
                    ReportStatistics.mean(metrics, r -> r.getFirstEchelonVehicleCount()),
                    ReportStatistics.mean(metrics, r -> r.getSecondEchelonVehicleCount()),
                    ReportStatistics.mean(metrics, RunResult::getTotalWaitingTime),
                    ReportStatistics.mean(metrics, r -> r.getBestIteration()),
                    ReportStatistics.mean(metrics, RunResult::getRuntimeSeconds),
                    ReportStatistics.coefficientOfVariation(metrics, RunResult::getRuntimeSeconds),
                    ReportStatistics.mean(metrics, RunResult::getDeterministicObjective),
                    ReportStatistics.mean(metrics, RunResult::getRecourseCost),
                    ReportStatistics.mean(metrics, RunResult::getAverageSuccessPercent),
                    ReportStatistics.min(metrics, RunResult::getMinimumSuccessPercent),
                    validationWorstMinimum,
                    ReportStatistics.mean(metrics, r -> r.getCustomersBelowRequiredSuccess()),
                    ReportStatistics.mean(metrics, RunResult::getStochasticEvaluationTimeSeconds),
                    ReportStatistics.stochasticRuntimeSharePercent(metrics));
        }

        private static InstanceSummary overall(List<InstanceSummary> summaries) {
            int ok = summaries.stream().mapToInt(summary -> summary.successful).sum();
            int fail = summaries.stream().mapToInt(summary -> summary.failed).sum();
            int validationPass = summaries.stream().mapToInt(summary -> summary.finalValidationPassed).sum();
            int validationFail = summaries.stream().mapToInt(summary -> summary.finalValidationFailed).sum();
            int validationComplete = summaries.stream().mapToInt(summary -> summary.finalValidationComplete).sum();
            int validationError = summaries.stream().mapToInt(summary -> summary.finalValidationError).sum();
            int validationNotRun = summaries.stream().mapToInt(summary -> summary.finalValidationNotRun).sum();
            double overallAverageRuntime = avg(summaries, s -> s.averageRuntime);
            double overallAverageStochasticTime = avg(summaries, s -> s.averageStochasticTime);
            double overallStochasticShare = Double.isFinite(overallAverageRuntime) && overallAverageRuntime > 0.0
                    ? overallAverageStochasticTime / overallAverageRuntime * 100.0
                    : Double.NaN;
            return new InstanceSummary(
                    "OVERALL AVG", ok, fail,
                    validationPass, validationFail, validationComplete, validationError, validationNotRun,
                    avg(summaries, s -> s.averageObjective), avg(summaries, s -> s.bestObjective), avg(summaries, s -> s.objectiveCv),
                    avg(summaries, s -> s.averageEvents), avg(summaries, s -> s.averageFirstEchelonVehicles),
                    avg(summaries, s -> s.averageSecondEchelonVehicles), avg(summaries, s -> s.averageWaiting),
                    avg(summaries, s -> s.averageBestIteration), overallAverageRuntime, avg(summaries, s -> s.runtimeCv),
                    avg(summaries, s -> s.averageDeterministicCost),
                    avg(summaries, s -> s.averageFailureCost),
                    avg(summaries, s -> s.averageSuccessPercent), minSummary(summaries, s -> s.worstMinimumSuccessPercent),
                    minSummary(summaries, s -> s.finalValidationWorstMinimumSuccessPercent),
                    avg(summaries, s -> s.averageBelowRequired), overallAverageStochasticTime,
                    overallStochasticShare);
        }

        private String deterministicRow() {
            return String.format(Locale.US,
                    "%-28s %4d %5d %12s %12s %8s %8s %7s %7s %10s %10s %10s %9s%n",
                    shorten(instance, 28), successful, failed, value(averageObjective), value(bestObjective),
                    plain2(objectiveCv), value(averageEvents),
                    value(averageFirstEchelonVehicles), value(averageSecondEchelonVehicles), value(averageWaiting),
                    value(averageBestIteration), durationValue(averageRuntime), plain2(runtimeCv));
        }

        private String deterministicOverallRow() { return deterministicRow(); }

        private String stochasticCostRow() {
            return String.format(Locale.US,
                    "%-28s %5d %7d %5d %5d %5d %5d %5d %11s %11s %8s %11s %10s %8s %9s %9s %9s %10s %8s %10s %8s%n",
                    shorten(instance, 28), successful, failed,
                    finalValidationPassed, finalValidationFailed, finalValidationComplete, finalValidationError, finalValidationNotRun,
                    value(averageObjective), value(bestObjective), plain2(objectiveCv),
                    value(averageDeterministicCost), value(averageFailureCost), percentPlain(averageSuccessPercent),
                    percentPlain(worstMinimumSuccessPercent), percentPlain(finalValidationWorstMinimumSuccessPercent),
                    value(averageBelowRequired), durationValue(averageStochasticTime),
                    percentPlain(averageStochasticRuntimePercent), durationValue(averageRuntime), plain2(runtimeCv));
        }

        private String stochasticCostOverallRow() { return stochasticCostRow(); }

        private String structureRow() {
            return String.format(Locale.US,
                    "%-28s %10s %8s %8s %12s %12s%n",
                    shorten(instance, 28), value(averageEvents), value(averageFirstEchelonVehicles),
                    value(averageSecondEchelonVehicles), value(averageWaiting), value(averageBestIteration));
        }

        private String structureOverallRow() { return structureRow(); }

        private interface SummaryMetric { double value(InstanceSummary summary); }

        private static double avg(List<InstanceSummary> summaries, SummaryMetric metric) {
            double sum = 0.0;
            int count = 0;
            for (InstanceSummary summary : summaries) {
                double value = metric.value(summary);
                if (Double.isFinite(value)) {
                    sum += value;
                    count++;
                }
            }
            return count == 0 ? Double.NaN : sum / count;
        }

        private static double minSummary(List<InstanceSummary> summaries, SummaryMetric metric) {
            double minimum = Double.POSITIVE_INFINITY;
            boolean found = false;
            for (InstanceSummary summary : summaries) {
                double value = metric.value(summary);
                if (Double.isFinite(value)) {
                    minimum = Math.min(minimum, value);
                    found = true;
                }
            }
            return found ? minimum : Double.NaN;
        }
    }

    private static String plain2(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "-";
    }

    private static final class SuccessStats {
        private final double averageSuccessPercent;
        private final double minimumSuccessPercent;
        private final int customersBelowRequiredSuccess;

        private SuccessStats(double averageSuccessPercent, double minimumSuccessPercent, int customersBelowRequiredSuccess) {
            this.averageSuccessPercent = averageSuccessPercent;
            this.minimumSuccessPercent = minimumSuccessPercent;
            this.customersBelowRequiredSuccess = customersBelowRequiredSuccess;
        }

        private static SuccessStats unavailable() {
            return new SuccessStats(Double.NaN, Double.NaN, -1);
        }
    }
}
