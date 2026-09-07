package config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import problem.ProblemVariant;

/**
 * Loads and validates the experiment configuration.
 * Invalid or missing settings are reported before the search starts.
 */
public final class ConfigurationLoader {

    private static final Set<String> SUPPORTED_KEYS = new HashSet<>(Arrays.asList(
            "inputDirectory", "resultDirectory", "executionEnvironment", "output.batchLabel",
            "output.consoleProgressInterval",
            "validation.finalSolution", "validation.finalExactSchedule", "validation.finalMonteCarlo",
            "validation.saCheckpointInterval", "schedulingDiagnosticsDetailed",
            "problemVariant", "evaluationMode", "stochasticMode",
            "numberOfDepots", "numberOfParkingLocations", "failureProbability",
            "customerCountStart", "customerCountEnd", "sampleIndexStart", "sampleIndexEnd",
            "searchSeedStart", "numberOfRuns", "distributionSeed",
            "initialSolutionAttempts", "maxSaIterations", "numberOfOperators",
            "initialVirtualMeetingCopiesPerCustomer", "initialDeparturePositionRatio",
            "maxLnsIterations", "operatorRepetitions",
            "adaptiveOperatorWeightsEnabled", "operatorWeightResetInterval", "operatorInitialWeight",
            "schedulingMode", "dynamicVirtualMeetingCopiesEnabled",
            "dynamicVirtualMeetingCopiesMultiplier", "routeScoreInfeasibilityPenalty",
            "timeFeasibilityTolerance", "feasibleMeetingTimeTolerance",
            "minimumPositiveDepartureTime", "departureReductionFactor",
            "routeStartTighteningEnabled", "recourseRelativeImprovementTolerance",
            "saInitialAcceptanceProbability", "saFinalAcceptanceProbability",
            "cdfGridPoints", "previousArrivalSubIntervals",
            "stochasticLowerQuantile", "stochasticUpperQuantile",
            "stochasticTailStrategy", "stochasticEvaluator", "finalSimulationReplications",
            "simulationSearchReplications", "simulationThreads",
            "simulationSearchSeed", "finalSimulationSeed",
            "simulationUseCommonRandomNumbers",
            "simulationLatenessTolerance", "simulationZeroTimeEpsilon",
            "serviceTimeCvMin", "serviceTimeCvMax", "travelTimeCvMin", "travelTimeCvMax",
            "zeroDistanceTravelCv", "minimumLognormalValue",
            "hourlyWageCoefficient", "fuelCostCoefficient", "penaltyCoefficient",
            "meetingPointAccessibility", "outerZoneRadiusKm", "cityCenterX", "cityCenterY",
            "deterministicTimeMode", "robustQuantile"));

    private ConfigurationLoader() {
    }

    public static ProjectSettings loadAndApply(Path configPath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        }

        validateNoUnknownProperties(properties);
        ProjectSettings projectSettings = apply(properties);
        return projectSettings;
    }

    private static ProjectSettings apply(Properties p) {
        // -----------------------------------------------------------------
        // Paths and execution mode
        // -----------------------------------------------------------------
        String inputDirectory = requiredString(p, "inputDirectory");
        String resultDirectory = requiredString(p, "resultDirectory");
        ExecutionEnvironment executionEnvironment = ExecutionEnvironment.fromConfig(
                requiredString(p, "executionEnvironment"));
        ExperimentParameters.batchLabel = requiredBatchLabel(p, "output.batchLabel");
        ExperimentParameters.consoleProgressInterval = requiredInt(p, "output.consoleProgressInterval");
        ExperimentParameters.validateFinalSolution = requiredBoolean(p, "validation.finalSolution");
        ExperimentParameters.validateFinalExactSchedule = optionalBoolean(
                p, "validation.finalExactSchedule", true);
        ExperimentParameters.validateFinalMonteCarlo = optionalBoolean(
                p, "validation.finalMonteCarlo", true);
        ExperimentParameters.validationSaCheckpointInterval = requiredInt(p, "validation.saCheckpointInterval");
        ExperimentParameters.schedulingDiagnosticsDetailed = optionalBoolean(
                p, "schedulingDiagnosticsDetailed", false);

        ProblemVariant selectedProblemVariant = ProblemVariant.fromConfig(
                requiredString(p, "problemVariant"));

        // The configured variant selects an input subdirectory.
        // BenchmarkInstanceReader checks the instance file itself.
        Path inputPath = Path.of(inputDirectory);
        Path resultPath = Path.of(resultDirectory);
        Path instancePath = inputPath.resolve(
                selectedProblemVariant.inputDirectoryName());

        ExperimentParameters.evaluationMode = EvaluationMode.fromConfig(
                requiredString(p, "evaluationMode"));

        if (ExperimentParameters.isStochasticEvaluation()) {
            ExperimentParameters.stochasticMode = StochasticMode.fromConfig(
                    requiredString(p, "stochasticMode"));
        } else {
            String stochasticMode = p.getProperty("stochasticMode");
            if (stochasticMode != null && !stochasticMode.trim().isEmpty()) {
                fail("stochasticMode must be omitted when evaluationMode=DETERMINISTIC.");
            }
            ExperimentParameters.stochasticMode = null;
        }

        // -----------------------------------------------------------------
        // Problem and experiment range
        // -----------------------------------------------------------------
        int selectedDepotCount = requiredInt(p, "numberOfDepots");
        int selectedParkingCount = requiredInt(p, "numberOfParkingLocations");
        ProblemParameters.failureProbability = requiredDouble(p, "failureProbability");

        ExperimentParameters.customerCountStart = requiredInt(p, "customerCountStart");
        ExperimentParameters.customerCountEnd = requiredInt(p, "customerCountEnd");
        ExperimentParameters.sampleIndexStart = requiredInt(p, "sampleIndexStart");
        ExperimentParameters.sampleIndexEnd = requiredInt(p, "sampleIndexEnd");
        ExperimentParameters.searchSeedStart = requiredInt(p, "searchSeedStart");
        ExperimentParameters.numberOfRuns = requiredInt(p, "numberOfRuns");
        ExperimentParameters.distributionSeed = requiredLong(p, "distributionSeed");

        // -----------------------------------------------------------------
        // Search and initialization
        // -----------------------------------------------------------------
        ExperimentParameters.initialSolutionAttempts = requiredInt(p, "initialSolutionAttempts");
        ExperimentParameters.maxSaIterations = requiredInt(p, "maxSaIterations");
        ExperimentParameters.numberOfOperators = requiredInt(p, "numberOfOperators");
        ExperimentParameters.initialVirtualMeetingCopiesPerCustomer = requiredDouble(p, "initialVirtualMeetingCopiesPerCustomer");
        ExperimentParameters.initialDeparturePositionRatio = requiredDouble(p, "initialDeparturePositionRatio");
        ExperimentParameters.maxLnsIterations = requiredInt(p, "maxLnsIterations");
        ExperimentParameters.operatorRepetitions = requiredInt(p, "operatorRepetitions");

        // -----------------------------------------------------------------
        // Adaptive search and approximate scheduling
        // -----------------------------------------------------------------
        ExperimentParameters.adaptiveOperatorWeightsEnabled = requiredBoolean(p, "adaptiveOperatorWeightsEnabled");
        ExperimentParameters.operatorWeightResetInterval = requiredInt(p, "operatorWeightResetInterval");
        ExperimentParameters.operatorInitialWeight = requiredDouble(p, "operatorInitialWeight");

        ExperimentParameters.schedulingMode = SchedulingMode.fromConfig(requiredString(p, "schedulingMode"));
        ExperimentParameters.dynamicVirtualMeetingCopiesEnabled = requiredBoolean(p, "dynamicVirtualMeetingCopiesEnabled");
        ExperimentParameters.dynamicVirtualMeetingCopiesMultiplier = requiredInt(p, "dynamicVirtualMeetingCopiesMultiplier");
        ExperimentParameters.routeScoreInfeasibilityPenalty = requiredDouble(p, "routeScoreInfeasibilityPenalty");
        ExperimentParameters.timeFeasibilityTolerance = requiredDouble(p, "timeFeasibilityTolerance");
        ExperimentParameters.feasibleMeetingTimeTolerance = requiredDouble(p, "feasibleMeetingTimeTolerance");
        ExperimentParameters.minimumPositiveDepartureTime = requiredDouble(p, "minimumPositiveDepartureTime");
        // One shared reduction factor is used by deterministic DAS repair and by
        // both stochastic departure-adjustment modes (CCM and PBM).
        ExperimentParameters.departureReductionFactor = optionalDouble(p, "departureReductionFactor", 0.5);
        ExperimentParameters.routeStartTighteningEnabled = optionalBoolean(p, "routeStartTighteningEnabled", false);
        ExperimentParameters.recourseRelativeImprovementTolerance = requiredDouble(p, "recourseRelativeImprovementTolerance");

        // -----------------------------------------------------------------
        // Simulated annealing
        // -----------------------------------------------------------------
        ExperimentParameters.saInitialAcceptanceProbability = requiredDouble(p, "saInitialAcceptanceProbability");
        ExperimentParameters.saFinalAcceptanceProbability = requiredDouble(p, "saFinalAcceptanceProbability");

        // -----------------------------------------------------------------
        // Stochastic approximation and simulation
        // -----------------------------------------------------------------
        ExperimentParameters.cdfGridPoints = requiredInt(p, "cdfGridPoints");
        ExperimentParameters.previousArrivalSubIntervals = requiredInt(p, "previousArrivalSubIntervals");
        ExperimentParameters.stochasticLowerQuantile = requiredDouble(p, "stochasticLowerQuantile");
        ExperimentParameters.stochasticUpperQuantile = requiredDouble(p, "stochasticUpperQuantile");
        ExperimentParameters.stochasticTailStrategy = upper(requiredString(p, "stochasticTailStrategy"));
        ExperimentParameters.stochasticEvaluator = upper(requiredString(p, "stochasticEvaluator"));
        ExperimentParameters.finalSimulationReplications = requiredInt(p, "finalSimulationReplications");
        ExperimentParameters.simulationSearchReplications = requiredInt(p, "simulationSearchReplications");
        ExperimentParameters.simulationThreads = requiredInt(p, "simulationThreads");
        ExperimentParameters.simulationSearchSeed = requiredLong(p, "simulationSearchSeed");
        ExperimentParameters.finalSimulationSeed = requiredLong(p, "finalSimulationSeed");
        ExperimentParameters.simulationUseCommonRandomNumbers = optionalBoolean(
                p, "simulationUseCommonRandomNumbers", true);
        ExperimentParameters.simulationLatenessTolerance = requiredDouble(p, "simulationLatenessTolerance");
        ExperimentParameters.simulationZeroTimeEpsilon = requiredDouble(p, "simulationZeroTimeEpsilon");
        ExperimentParameters.serviceTimeCvMin = requiredDouble(p, "serviceTimeCvMin");
        ExperimentParameters.serviceTimeCvMax = requiredDouble(p, "serviceTimeCvMax");
        ExperimentParameters.travelTimeCvMin = requiredDouble(p, "travelTimeCvMin");
        ExperimentParameters.travelTimeCvMax = requiredDouble(p, "travelTimeCvMax");
        ExperimentParameters.zeroDistanceTravelCv = requiredDouble(p, "zeroDistanceTravelCv");
        ExperimentParameters.minimumLognormalValue = requiredDouble(p, "minimumLognormalValue");

        // -----------------------------------------------------------------
        // Sensitivity and problem variants
        // -----------------------------------------------------------------
        ExperimentParameters.hourlyWageCoefficient = requiredDouble(p, "hourlyWageCoefficient");
        ExperimentParameters.fuelCostCoefficient = requiredDouble(p, "fuelCostCoefficient");
        ExperimentParameters.penaltyCoefficient = requiredDouble(p, "penaltyCoefficient");

        ExperimentParameters.meetingPointAccessibility = upper(requiredString(p, "meetingPointAccessibility"));
        ExperimentParameters.outerZoneRadiusKm = requiredDouble(p, "outerZoneRadiusKm");
        ExperimentParameters.cityCenterX = requiredDouble(p, "cityCenterX");
        ExperimentParameters.cityCenterY = requiredDouble(p, "cityCenterY");
        ExperimentParameters.deterministicTimeMode = upper(requiredString(p, "deterministicTimeMode"));
        ExperimentParameters.robustQuantile = requiredDouble(p, "robustQuantile");

        validate(selectedProblemVariant, selectedDepotCount, selectedParkingCount);
        return new ProjectSettings(
                executionEnvironment,
                instancePath,
                resultPath,
                selectedProblemVariant,
                selectedDepotCount,
                selectedParkingCount);
    }

    private static void validate(
            ProblemVariant selectedProblemVariant,
            int selectedDepotCount,
            int selectedParkingCount) {
        positive(selectedDepotCount, "numberOfDepots");
        positive(selectedParkingCount, "numberOfParkingLocations");
        probability(ProblemParameters.failureProbability, "failureProbability");

        positive(ExperimentParameters.customerCountStart, "customerCountStart");
        if (ExperimentParameters.customerCountEnd < ExperimentParameters.customerCountStart) {
            fail("customerCountEnd must be >= customerCountStart.");
        }
        positive(ExperimentParameters.sampleIndexStart, "sampleIndexStart");
        if (ExperimentParameters.sampleIndexEnd < ExperimentParameters.sampleIndexStart) {
            fail("sampleIndexEnd must be >= sampleIndexStart.");
        }
        positive(ExperimentParameters.numberOfRuns, "numberOfRuns");
        positive(ExperimentParameters.consoleProgressInterval, "output.consoleProgressInterval");
        nonNegative(ExperimentParameters.validationSaCheckpointInterval, "validation.saCheckpointInterval");
        positive(ExperimentParameters.initialSolutionAttempts, "initialSolutionAttempts");
        positive(ExperimentParameters.maxSaIterations, "maxSaIterations");
        positive(ExperimentParameters.numberOfOperators, "numberOfOperators");
        if (ExperimentParameters.numberOfOperators > 18) {
            fail("numberOfOperators must be between 1 and 18 for the all-second-echelon portfolio.");
        }
        inRangeInclusive(ExperimentParameters.initialVirtualMeetingCopiesPerCustomer, 0.0, 1.0,
                "initialVirtualMeetingCopiesPerCustomer", false, true);
        inRangeInclusive(ExperimentParameters.initialDeparturePositionRatio, 0.0, 1.0,
                "initialDeparturePositionRatio", true, true);

        positive(ExperimentParameters.maxLnsIterations, "maxLnsIterations");
        positive(ExperimentParameters.operatorRepetitions, "operatorRepetitions");

        if (ExperimentParameters.adaptiveOperatorWeightsEnabled) {
            positive(ExperimentParameters.operatorWeightResetInterval, "operatorWeightResetInterval");
        } else {
            nonNegative(ExperimentParameters.operatorWeightResetInterval, "operatorWeightResetInterval");
        }
        positive(ExperimentParameters.operatorInitialWeight, "operatorInitialWeight");
        positive(ExperimentParameters.dynamicVirtualMeetingCopiesMultiplier, "dynamicVirtualMeetingCopiesMultiplier");
        nonNegative(ExperimentParameters.routeScoreInfeasibilityPenalty, "routeScoreInfeasibilityPenalty");
        positive(ExperimentParameters.timeFeasibilityTolerance, "timeFeasibilityTolerance");
        positive(ExperimentParameters.feasibleMeetingTimeTolerance, "feasibleMeetingTimeTolerance");
        positive(ExperimentParameters.minimumPositiveDepartureTime, "minimumPositiveDepartureTime");
        inRangeInclusive(ExperimentParameters.departureReductionFactor, 0.0, 1.0,
                "departureReductionFactor", false, false);
        nonNegative(ExperimentParameters.recourseRelativeImprovementTolerance, "recourseRelativeImprovementTolerance");

        probability(ExperimentParameters.saInitialAcceptanceProbability, "saInitialAcceptanceProbability");
        probability(ExperimentParameters.saFinalAcceptanceProbability, "saFinalAcceptanceProbability");
        if (ExperimentParameters.saInitialAcceptanceProbability <= ExperimentParameters.saFinalAcceptanceProbability) {
            fail("saInitialAcceptanceProbability must be greater than saFinalAcceptanceProbability.");
        }

        if (ExperimentParameters.cdfGridPoints <= 1) {
            fail("cdfGridPoints must be greater than 1.");
        }
        positive(ExperimentParameters.previousArrivalSubIntervals, "previousArrivalSubIntervals");
        probability(ExperimentParameters.stochasticLowerQuantile, "stochasticLowerQuantile");
        probability(ExperimentParameters.stochasticUpperQuantile, "stochasticUpperQuantile");
        if (ExperimentParameters.stochasticLowerQuantile >= ExperimentParameters.stochasticUpperQuantile) {
            fail("stochasticLowerQuantile must be smaller than stochasticUpperQuantile.");
        }
        oneOf(ExperimentParameters.stochasticTailStrategy, "stochasticTailStrategy", "CLIPPING", "TRUNCATION");
        oneOf(ExperimentParameters.stochasticEvaluator, "stochasticEvaluator", "SAS", "SIMULATION");
        positive(ExperimentParameters.finalSimulationReplications, "finalSimulationReplications");
        positive(ExperimentParameters.simulationSearchReplications, "simulationSearchReplications");
        positive(ExperimentParameters.simulationThreads, "simulationThreads");
        positive(ExperimentParameters.simulationLatenessTolerance, "simulationLatenessTolerance");
        positive(ExperimentParameters.simulationZeroTimeEpsilon, "simulationZeroTimeEpsilon");
        positive(ExperimentParameters.serviceTimeCvMin, "serviceTimeCvMin");
        positive(ExperimentParameters.serviceTimeCvMax, "serviceTimeCvMax");
        if (ExperimentParameters.serviceTimeCvMax < ExperimentParameters.serviceTimeCvMin) {
            fail("serviceTimeCvMax must be >= serviceTimeCvMin.");
        }
        positive(ExperimentParameters.travelTimeCvMin, "travelTimeCvMin");
        positive(ExperimentParameters.travelTimeCvMax, "travelTimeCvMax");
        if (ExperimentParameters.travelTimeCvMax < ExperimentParameters.travelTimeCvMin) {
            fail("travelTimeCvMax must be >= travelTimeCvMin.");
        }
        positive(ExperimentParameters.zeroDistanceTravelCv, "zeroDistanceTravelCv");
        positive(ExperimentParameters.minimumLognormalValue, "minimumLognormalValue");

        positive(ExperimentParameters.hourlyWageCoefficient, "hourlyWageCoefficient");
        positive(ExperimentParameters.fuelCostCoefficient, "fuelCostCoefficient");
        nonNegative(ExperimentParameters.penaltyCoefficient, "penaltyCoefficient");

        oneOf(ExperimentParameters.meetingPointAccessibility, "meetingPointAccessibility",
                "FULL", "PARKING_ONLY", "OUTER_ZONE");
        positive(ExperimentParameters.outerZoneRadiusKm, "outerZoneRadiusKm");
        oneOf(ExperimentParameters.deterministicTimeMode, "deterministicTimeMode",
                "MEAN", "ROBUST_QUANTILE");
        probability(ExperimentParameters.robustQuantile, "robustQuantile");
        if (ExperimentParameters.usesRobustDeterministicTimes()
                && !ExperimentParameters.useStochasticTruncation()) {
            fail("deterministicTimeMode=ROBUST_QUANTILE defines its quantile on the active "
                    + "truncated stochastic distribution and therefore requires stochasticTailStrategy=TRUNCATION.");
        }
        if (ExperimentParameters.usesRobustDeterministicTimes()
                && !ExperimentParameters.isDeterministicEvaluation()) {
            fail("deterministicTimeMode=ROBUST_QUANTILE is a conservative deterministic benchmark "
                    + "and requires evaluationMode=DETERMINISTIC.");
        }

        if (ExperimentParameters.isStochasticEvaluation()
                && ExperimentParameters.schedulingMode == SchedulingMode.EXACT) {
            fail("evaluationMode=STOCHASTIC requires schedulingMode=APPROXIMATE; "
                    + "schedulingMode=EXACT is reserved for deterministic scheduling or final verification.");
        }

        if (selectedProblemVariant.isDellaert()) {
            if (!ExperimentParameters.isDeterministicEvaluation()) {
                fail("problemVariant=DELLAERT-2E-VRP requires evaluationMode=DETERMINISTIC.");
            }
            if (!"MEAN".equals(ExperimentParameters.deterministicTimeMode)) {
                fail("problemVariant=DELLAERT-2E-VRP requires deterministicTimeMode=MEAN.");
            }
            if (!"PARKING_ONLY".equals(ExperimentParameters.meetingPointAccessibility)) {
                fail("problemVariant=DELLAERT-2E-VRP requires meetingPointAccessibility=PARKING_ONLY.");
            }
            if (ExperimentParameters.schedulingMode != SchedulingMode.APPROXIMATE) {
                fail("problemVariant=DELLAERT-2E-VRP requires schedulingMode=APPROXIMATE.");
            }
        }

    }


    private static void validateNoUnknownProperties(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!SUPPORTED_KEYS.contains(key)) {
                fail("Unknown configuration property '" + key
                        + "'. Remove it or correct the property name.");
            }
        }
    }

    private static String requiredBatchLabel(Properties p, String key) {
        String value = requiredString(p, key);
        if (!value.matches("[A-Za-z0-9._-]+")) {
            fail(key + " may contain only letters, numbers, period, underscore and hyphen.");
        }
        return value;
    }

    private static String requiredString(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            fail("Missing required configuration property: " + key);
        }
        return value.trim();
    }


    private static int requiredInt(Properties p, String key) {
        String value = requiredString(p, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(key + " must be an integer, but was: " + value, numberFormatException);
        }
    }

    private static int optionalInt(Properties p, String key, int defaultValue) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(
                    key + " must be an integer, but was: " + value,
                    numberFormatException);
        }
    }

    private static long requiredLong(Properties p, String key) {
        String value = requiredString(p, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(key + " must be a long integer, but was: " + value, numberFormatException);
        }
    }

    private static double requiredDouble(Properties p, String key) {
        String value = requiredString(p, key);
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                fail(key + " must be finite, but was: " + value);
            }
            return parsed;
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(key + " must be numeric, but was: " + value, numberFormatException);
        }
    }

    private static double optionalDouble(Properties p, String key, double defaultValue) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed)) {
                fail(key + " must be finite, but was: " + value);
            }
            return parsed;
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(
                    key + " must be numeric, but was: " + value,
                    numberFormatException);
        }
    }

    private static String optionalString(Properties p, String key, String defaultValue) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static boolean optionalBoolean(Properties p, String key, boolean defaultValue) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        fail(key + " must be true or false, but was: " + value);
        return defaultValue;
    }

    private static boolean requiredBoolean(Properties p, String key) {
        String value = requiredString(p, key).toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false, but was: " + value);
    }


    private static String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    private static void oneOf(String value, String name, String... options) {
        for (String option : options) {
            if (option.equals(value)) {
                return;
            }
        }
        fail(name + " must be one of " + Arrays.toString(options) + ", but was: " + value);
    }

    private static void probability(double value, String name) {
        if (!(value > 0.0 && value < 1.0)) {
            fail(name + " must be strictly between 0 and 1.");
        }
    }

    private static void positive(int value, String name) {
        if (value <= 0) {
            fail(name + " must be positive.");
        }
    }

    private static void positive(double value, String name) {
        if (!(value > 0.0)) {
            fail(name + " must be positive.");
        }
    }

    private static void nonNegative(int value, String name) {
        if (value < 0) {
            fail(name + " must be non-negative.");
        }
    }

    private static void nonNegative(double value, String name) {
        if (value < 0.0) {
            fail(name + " must be non-negative.");
        }
    }

    private static void inRangeInclusive(
            double value,
            double lower,
            double upper,
            String name,
            boolean includeLower,
            boolean includeUpper) {
        boolean isLowerBoundSatisfied = includeLower ? value >= lower : value > lower;
        boolean isUpperBoundSatisfied = includeUpper ? value <= upper : value < upper;
        if (!isLowerBoundSatisfied || !isUpperBoundSatisfied) {
            fail(name + " must be "
                    + (includeLower ? "[" : "(") + lower + ", " + upper
                    + (includeUpper ? "]" : ")") + ".");
        }
    }

    private static void fail(String message) {
        throw new IllegalArgumentException("Invalid config.properties: " + message);
    }
}
