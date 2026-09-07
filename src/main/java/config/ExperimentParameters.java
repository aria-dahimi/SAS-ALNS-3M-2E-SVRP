package config;

/**
 * Experiment and search parameters loaded from {@code config.properties}.
 * Per-run state is kept outside this class.
 */
public final class ExperimentParameters {

    private ExperimentParameters() {
    }

    // ---------------------------------------------------------------------
    // Evaluation regime
    // ---------------------------------------------------------------------
    public static EvaluationMode evaluationMode;

    public static StochasticMode stochasticMode;

    public static boolean isDeterministicEvaluation() {
        return evaluationMode == EvaluationMode.DETERMINISTIC;
    }

    public static boolean isStochasticEvaluation() {
        return evaluationMode == EvaluationMode.STOCHASTIC;
    }

    public static boolean usesCcm() {
        return isStochasticEvaluation() && stochasticMode == StochasticMode.CCM;
    }

    public static boolean usesPbm() {
        return isStochasticEvaluation() && stochasticMode == StochasticMode.PBM;
    }

    // ---------------------------------------------------------------------
    // Batch output
    // ---------------------------------------------------------------------
    public static String batchLabel;

    public static int consoleProgressInterval;

    // ---------------------------------------------------------------------
    // Lightweight solution validation
    // ---------------------------------------------------------------------
    public static boolean validateFinalSolution;

    public static boolean validateFinalExactSchedule;

    public static boolean validateFinalMonteCarlo;

    public static boolean schedulingDiagnosticsDetailed;

    /**
     * Validate the global incumbent after every N completed SA cycles.
     * A value of 0 disables intermediate validation.
     */
    public static int validationSaCheckpointInterval;

    public static boolean shouldValidateAfterSaCycle(int completedSaCycles) {
        return validationSaCheckpointInterval > 0
                && completedSaCycles > 0
                && completedSaCycles % validationSaCheckpointInterval == 0;
    }

    // ---------------------------------------------------------------------
    // Experiment range and randomization
    // ---------------------------------------------------------------------
    public static int customerCountStart;
    public static int customerCountEnd;
    public static int sampleIndexStart;
    public static int sampleIndexEnd;

    public static int searchSeedStart;
    public static int numberOfRuns;

    public static long distributionSeed;

    // ---------------------------------------------------------------------
    // Search effort and initialization
    // ---------------------------------------------------------------------
    public static int initialSolutionAttempts;
    public static int maxSaIterations;
    public static int numberOfOperators;

    public static double initialDeparturePositionRatio;

    public static int maxLnsIterations;

    public static int operatorRepetitions;

    // ---------------------------------------------------------------------
    // Adaptive operator selection
    // ---------------------------------------------------------------------
    public static boolean adaptiveOperatorWeightsEnabled;
    public static int operatorWeightResetInterval;
    public static double operatorInitialWeight;

    // ---------------------------------------------------------------------
    // Approximate scheduling and feasibility controls
    // ---------------------------------------------------------------------
    public static SchedulingMode schedulingMode;

    public static boolean usesApproximateScheduling() {
        return schedulingMode == SchedulingMode.APPROXIMATE;
    }
    public static double initialVirtualMeetingCopiesPerCustomer;
    public static boolean dynamicVirtualMeetingCopiesEnabled;
    public static int dynamicVirtualMeetingCopiesMultiplier;

    public static double routeScoreInfeasibilityPenalty;

    public static double timeFeasibilityTolerance;

    public static double feasibleMeetingTimeTolerance;

    public static double minimumPositiveDepartureTime;

    /**
     * Shared multiplier used when moving departures earlier in deterministic DAS
     * repair, CCM reliability repair, and PBM recourse optimization.
     */
    public static double departureReductionFactor;

    public static boolean routeStartTighteningEnabled;

    public static double recourseRelativeImprovementTolerance;

    // ---------------------------------------------------------------------
    // Simulated annealing calibration
    // ---------------------------------------------------------------------
    public static double saInitialAcceptanceProbability;
    public static double saFinalAcceptanceProbability;

    // ---------------------------------------------------------------------
    // Stochastic approximation
    // ---------------------------------------------------------------------
    public static int cdfGridPoints;

    public static int previousArrivalSubIntervals;
    public static double stochasticLowerQuantile;
    public static double stochasticUpperQuantile;
    public static String stochasticTailStrategy;


    public static String stochasticEvaluator;
    public static int finalSimulationReplications;
    public static int simulationSearchReplications;
    public static int simulationThreads;
    /** Direct Monte Carlo seed used for search-time simulation. */
    public static long simulationSearchSeed;

    /** Direct Monte Carlo seed used for independent final validation. */
    public static long finalSimulationSeed;

    /** Reuse common random numbers for search-time simulation comparisons. */
    public static boolean simulationUseCommonRandomNumbers;

    public static double simulationLatenessTolerance;

    public static double simulationZeroTimeEpsilon;

    public static double serviceTimeCvMin;
    public static double serviceTimeCvMax;

    public static double travelTimeCvMin;
    public static double travelTimeCvMax;

    public static double zeroDistanceTravelCv;

    public static double minimumLognormalValue;


    public static boolean useStochasticTruncation() {
        return "TRUNCATION".equalsIgnoreCase(stochasticTailStrategy);
    }

    // ---------------------------------------------------------------------
    // Sensitivity parameters
    // ---------------------------------------------------------------------
    public static double hourlyWageCoefficient;
    public static double fuelCostCoefficient;
    public static double penaltyCoefficient;

    // ---------------------------------------------------------------------
    // Meeting-point and deterministic-time variants
    // ---------------------------------------------------------------------
    public static String meetingPointAccessibility;
    public static double outerZoneRadiusKm;
    public static double cityCenterX;
    public static double cityCenterY;

    public static String deterministicTimeMode;
    /** Quantile within the ACTIVE truncated stochastic distribution, e.g. 0.95. */
    public static double robustQuantile;

    public static boolean usesRobustDeterministicTimes() {
        return "ROBUST_QUANTILE".equalsIgnoreCase(deterministicTimeMode);
    }

    /**
     * Maps a quantile of the active truncated stochastic distribution back to
     * the parent log-normal quantile used by Apache Commons Math.
     * Example: truncation [0.05,0.95] and robustQuantile=0.95 -> 0.905.
     */
    public static double robustParentQuantile() {
        double lower = stochasticLowerQuantile;
        double upper = stochasticUpperQuantile;
        return lower + robustQuantile * (upper - lower);
    }

}
