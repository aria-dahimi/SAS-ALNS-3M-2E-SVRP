package search;
import solution.Solution;
import solution.InitialSolutionConstructor;

import java.util.ArrayList;
import optimization.ExactScheduleOptimizer;
import optimization.ExactScheduleResult;
import optimization.ScheduleSnapshot;
import stochastic.StochasticMonteCarloResult;
import stochastic.StochasticMonteCarloValidator;
import problem.ProblemInstance;
import config.ExperimentParameters;
import config.ProblemParameters;
import experiment.RunContext;
import experiment.SearchProblemTemplates;
import validation.SolutionValidationException;
import validation.SolutionValidationResult;
import validation.SolutionValidator;

public class SearchEngine {

    ProblemInstance problem;
    private final RunContext runContext;
    private long runStartMillis;
    long bestTimestampMillis;

    double initialTemperature = 0;
    double finalTemperature = 0;
    double coolingRate = 0;

    public int totalLargeNeighborhoodSearchIterations = 0;
    public int infeasibleIterations = 0;
    public int acceptedIterations = 0;
    public int bestTotalIteration = 0;
    public int bestSimulatedAnnealingCycle = 0;

    ArrayList<Solution> initialPopulation;
    public Solution initialSolution;
    public Solution simulatedAnnealingBestSolution;
    public Solution finalSolution;

    public double initializationTimeSeconds = 0;
    public double runtimeSeconds = 0;
    public long   gurobiRuntimeMillis = 0;
    public ExactScheduleResult finalExactVerificationResult =
            ExactScheduleResult.skipped("NOT_RUN");
    public ExactScheduleResult finalFreeExactDiagnosticResult =
            ExactScheduleResult.skipped("NOT_RUN");
    public ScheduleSnapshot finalSearchScheduleSnapshot;
    public StochasticMonteCarloResult finalMonteCarloValidationResult;
    public double postSearchValidationTimeSeconds = 0.0;

    public double timeToBestSeconds = 0;
    public double stochasticEvaluationTimeSeconds = 0;
    public double recourseOptimizationTimeSeconds = 0;
    public double chanceRepairTimeSeconds = 0;
    public boolean searchCompleted = false;

    public int simulatedAnnealingCycle = 0;

    public OperatorWeightTable operatorWeightTable;

    public SimulatedAnnealing simulatedAnnealing;

    public SearchEngine(
            ProblemInstance problemInstance,
            SearchProblemTemplates templates,
            RunContext runContext) {
        if (problemInstance == null || templates == null || runContext == null) {
            throw new IllegalArgumentException(
                    "Problem, search templates and run context cannot be null.");
        }
        this.problem = problemInstance;
        this.runContext = runContext;
        this.initialPopulation = new ArrayList<>();
        this.initialSolution = Solution.fromPreparedProblem(
                templates.getInitialSolutionProblem(), runContext);
        this.finalSolution = Solution.fromPreparedProblem(
                templates.getFinalSolutionProblem(), runContext);
        this.operatorWeightTable = new OperatorWeightTable(
                runContext.getSearchRandomGenerator());
        this.resetOperatorWeights();
    }

    public RunContext getRunContext() {
        return runContext;
    }

    public ProblemInstance getProblem() {
        return problem;
    }

    public void execute() {
        long executionStartMillis = System.currentTimeMillis();
        this.runStartMillis = executionStartMillis;
        this.bestTimestampMillis = executionStartMillis;

        simulatedAnnealing = new SimulatedAnnealing(this, initialTemperature, coolingRate);
        initialPopulation.clear();
        this.generateInitialPopulation();

        if (initialPopulation.isEmpty()) {
            throw new NoInitialSolutionException(
                "No feasible initial solution found. Initial population is empty."
            );
        }

        this.selectBestInitialSolution();
        if (!isValidSolution(initialSolution)) {
            throw new NoInitialSolutionException(
                "No feasible initial solution found. Initial solution has max objective."
            );
        }

        this.calibrateAnnealingParameters();

        int initialPopulationSize = initialPopulation.size();
        finalizeSolutions(initialSolution, finalSolution);
        this.cleanUpSolutions();

        long searchStartMillis = System.currentTimeMillis();
        this.bestTimestampMillis = searchStartMillis;
        this.runtimeSeconds += elapsedSeconds(executionStartMillis, searchStartMillis);
        if (runContext.getReporter() != null) {
            runContext.getReporter().recordInitialSolution(
                    initialSolution, initialPopulationSize, initialTemperature, finalTemperature, coolingRate);
        }

        while (simulatedAnnealingCycle < ExperimentParameters.maxSaIterations) {
            performSimulatedAnnealing();
            this.resetOperatorWeights();

            simulatedAnnealingCycle++;
            long currentTime = System.currentTimeMillis();
            this.runtimeSeconds = elapsedSeconds(searchStartMillis, currentTime);

            if (ExperimentParameters.shouldValidateAfterSaCycle(simulatedAnnealingCycle)) {
                validateCheckpointSolution(simulatedAnnealingCycle);
            }
        }

        /* Keep completed search metrics even if a later final check fails. */
        long searchCompletedMillis = System.currentTimeMillis();
        this.runtimeSeconds = elapsedSeconds(executionStartMillis, searchCompletedMillis);
        this.timeToBestSeconds = elapsedSeconds(executionStartMillis, bestTimestampMillis);
        this.searchCompleted = true;

        long validationStartMillis = System.currentTimeMillis();

        /*
         * Validate the final search solution before optional exact or Monte Carlo
         * post-processing. Validation is read-only and does not modify the search result.
         */
        validateFinalSolutionBeforeDiagnostics();

        if (ExperimentParameters.validateFinalExactSchedule) {
            this.finalSearchScheduleSnapshot = ScheduleSnapshot.capture(this.finalSolution);
            if (ExperimentParameters.isDeterministicEvaluation()) {
                /* For deterministic runs, report the verified exact schedule. */
                this.finalExactVerificationResult =
                        ExactScheduleOptimizer.optimizeSchedule(this.finalSolution);
            } else {
                /* For stochastic runs, verify the search departures without reoptimizing them. */
                this.finalExactVerificationResult =
                        ExactScheduleOptimizer.verifySchedule(this.finalSolution, true);

                if (this.finalExactVerificationResult.isCertifiedFeasible()
                        && this.finalExactVerificationResult.getVerifiedSolution() != null
                        && ExperimentParameters.schedulingDiagnosticsDetailed) {
                    this.finalExactVerificationResult.getVerifiedSolution()
                            .evaluateStochasticAtFixedScheduleForDiagnostics();
                }

                if (ExperimentParameters.schedulingDiagnosticsDetailed) {
                    /* Optional comparison only; it never replaces the stochastic search schedule. */
                    this.finalFreeExactDiagnosticResult =
                            ExactScheduleOptimizer.verifySchedule(this.finalSolution, false);
                    if (this.finalFreeExactDiagnosticResult.isCertifiedFeasible()
                            && this.finalFreeExactDiagnosticResult.getVerifiedSolution() != null) {
                        this.finalFreeExactDiagnosticResult.getVerifiedSolution()
                                .evaluateStochasticAtFixedScheduleForDiagnostics();
                    }
                }
            }
        }

        initializationTimeSeconds = elapsedSeconds(executionStartMillis, searchStartMillis);
        timeToBestSeconds = elapsedSeconds(executionStartMillis, bestTimestampMillis);

        this.initialSolution.calculateAllDetails();
        this.finalSolution.calculateAllDetails();

        if (ExperimentParameters.isStochasticEvaluation()) {
            if (ExperimentParameters.usesCcm()) {
                this.finalSolution.findRecourseCost();
            }
            this.finalSolution.calculateStochasticFailures();
        }

        /*
         * Independent final MC evaluates the final solution for every 3M planning
         * approach without changing departures or feeding back into the search.
         * CCM reports PASS/FAIL; the other approaches report descriptive COMPLETE evaluations.
         */
        if (!ProblemParameters.isDellaert() && ExperimentParameters.validateFinalMonteCarlo) {
            this.finalMonteCarloValidationResult =
                    StochasticMonteCarloValidator.validate(this.finalSolution);
        }
        this.postSearchValidationTimeSeconds =
                elapsedSeconds(validationStartMillis, System.currentTimeMillis());

    }


    /** Runs the configured final validation before optional post-processing. */
    private void validateFinalSolutionBeforeDiagnostics() {
        if (!ExperimentParameters.validateFinalSolution) {
            return;
        }
        long startNanos = System.nanoTime();
        SolutionValidationResult validation = SolutionValidator.validate(this.finalSolution);
        double elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0;

        if (runContext.getReporter() != null) {
            runContext.getReporter().recordValidation("FINAL", validation, elapsedMillis);
        }
        if (!validation.isValid()) {
            throw new SolutionValidationException(
                    "Final solution validation failed before exact/Monte Carlo diagnostics:"
                    + System.lineSeparator() + validation.formatErrors());
        }
    }

    /** Runs the configured structural validation checkpoint. */
    private void validateCheckpointSolution(int completedSaCycle) {
        long startNanos = System.nanoTime();
        SolutionValidationResult validation = SolutionValidator.validate(this.finalSolution);
        double elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0;

        if (runContext.getReporter() != null) {
            runContext.getReporter().recordValidation(
                    "SA " + completedSaCycle + " checkpoint", validation, elapsedMillis);
        }

        if (!validation.isValid()) {
            throw new SolutionValidationException(
                    "Solution validation failed after SA cycle " + completedSaCycle + ":"
                    + System.lineSeparator() + validation.formatErrors());
        }
    }

    private void finalizeSolutions(Solution source, Solution target) {
        target.problem.alignVirtualMeetingCopiesWith(source.problem);
        target.copyFrom(source);
    }

    private void performSimulatedAnnealing() {
        simulatedAnnealing.reset(this, initialTemperature, coolingRate);
        simulatedAnnealing.execute();

        simulatedAnnealingBestSolution = simulatedAnnealing.bestSolution;
        if (simulatedAnnealingBestSolution.objective < finalSolution.objective) {
            finalizeSolutions(simulatedAnnealingBestSolution, finalSolution);
        }
    }

    public void generateInitialPopulation() {
        Solution initialCandidate = new Solution(this.problem, runContext);
        for (int i = 0; i < ExperimentParameters.initialSolutionAttempts; i++) {
            initialCandidate.reset();
            new InitialSolutionConstructor().construct(initialCandidate, this);

            if (initialCandidate.completionStatus == 2 && initialCandidate.feasibilityStatus == 0) {
                initialPopulation.add(initialCandidate);
                initialCandidate = new Solution(this.problem, runContext);
            } else {
                initialCandidate.clearSolution();
            }
        }
    }

    private void cleanUpSolutions() {
        for (Solution solution : this.initialPopulation) {
            if (solution != this.initialSolution) {
                solution.clearSolution();
                solution.problem.clear();
            }
        }
        this.initialPopulation.clear();
        this.initialPopulation.add(this.initialSolution);
    }


    public void calibrateAnnealingParameters() {
        double initialAcceptanceProbability = ExperimentParameters.saInitialAcceptanceProbability;
        double finalAcceptanceProbability = ExperimentParameters.saFinalAcceptanceProbability;
        double objectiveDifferenceEstimate = 0.0;

        for (int i = 0; i < initialPopulation.size(); i++) {
            Solution sampledSolutionA = initialPopulation.get(runContext.getSearchRandomGenerator().nextInt(initialPopulation.size()));
            Solution sampledSolutionB = initialPopulation.get(runContext.getSearchRandomGenerator().nextInt(initialPopulation.size()));
            objectiveDifferenceEstimate += Math.abs(sampledSolutionA.objective - sampledSolutionB.objective);
        }

        objectiveDifferenceEstimate /= initialPopulation.size();

        // A zero (or otherwise non-finite) spread makes T0/Tf zero/non-finite and
        // the logarithmic cooling-rate calculation can then produce NaN.  This
        // can legitimately happen when the sampled initial population has the
        // same objective throughout.  Use a tiny, objective-scaled positive
        // fallback so the SA schedule remains well-defined without materially
        // changing normal calibrations.
        if (!Double.isFinite(objectiveDifferenceEstimate) || objectiveDifferenceEstimate <= 0.0) {
            double objectiveScale = 0.0;
            for (Solution solution : initialPopulation) {
                if (Double.isFinite(solution.objective)) {
                    objectiveScale = Math.max(objectiveScale, Math.abs(solution.objective));
                }
            }
            objectiveDifferenceEstimate = Math.max(1.0e-9, objectiveScale * 1.0e-9);
        }

        initialTemperature = -objectiveDifferenceEstimate / Math.log(initialAcceptanceProbability);
        finalTemperature = -objectiveDifferenceEstimate / Math.log(finalAcceptanceProbability);
        coolingRate = Math.exp((Math.log(finalTemperature) - Math.log(initialTemperature)) / ExperimentParameters.maxLnsIterations);

        if (!Double.isFinite(initialTemperature) || initialTemperature <= 0.0
                || !Double.isFinite(finalTemperature) || finalTemperature <= 0.0
                || !Double.isFinite(coolingRate) || coolingRate <= 0.0) {
            throw new IllegalStateException("Invalid simulated-annealing calibration: T0="
                    + initialTemperature + ", Tf=" + finalTemperature + ", coolingRate=" + coolingRate
                    + ", objectiveDifferenceEstimate=" + objectiveDifferenceEstimate);
        }
    }

    public void selectBestInitialSolution() {
        Solution bestFeasibleInitialSolution = new Solution();
        boolean isFirstFeasible = true;

        for (Solution solution : this.initialPopulation) {
            if (solution.feasibilityStatus == 0 && solution.completionStatus == 2) {
                if (isFirstFeasible || solution.objective < bestFeasibleInitialSolution.objective) {
                    bestFeasibleInitialSolution = solution;
                    this.initialSolution = solution;
                    isFirstFeasible = false;
                }
            }
        }
    }

    void resetOperatorWeights() {
        double initialWeight = ExperimentParameters.operatorInitialWeight;
        for (int i = 0; i < ExperimentParameters.numberOfOperators; i++) {
            // DELLAERT fixes each satellite to its home parking, so operator 3 is excluded.
            if (ProblemParameters.isDellaert() && i == 3) {
                continue;
            }
            this.operatorWeightTable.setWeight(i, initialWeight);
        }
    }

    public void clear() {
        initialSolution = null;
        simulatedAnnealingBestSolution = null;
        finalSolution = null;

        if (initialPopulation != null) {
            initialPopulation.clear();
            initialPopulation = null;
        }

        operatorWeightTable.clear();
    }

    private static double elapsedSeconds(long startMillis, long endMillis) {
        return (endMillis - startMillis) / 1000.0;
    }

    public double currentRuntimeSeconds() {
        if (runStartMillis <= 0L) {
            return 0.0;
        }
        return elapsedSeconds(runStartMillis, System.currentTimeMillis());
    }

    public static class NoInitialSolutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NoInitialSolutionException(String message) {
            super(message);
        }
    }

    private boolean isValidSolution(Solution solution) {
        return solution != null
            && !Double.isNaN(solution.objective)
            && !Double.isInfinite(solution.objective)
            && solution.objective < Integer.MAX_VALUE;
    }
}
