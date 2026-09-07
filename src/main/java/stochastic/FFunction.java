package stochastic;

import org.apache.commons.math3.distribution.LogNormalDistribution;
import config.ExperimentParameters;
import config.ProblemParameters;
import problem.Node;

public class FFunction {

    public double[][] previousArrivalCdf; // Arrival-time CDF before travel to the next node.
    public double[] previousCdfMetadata;
    public double[][] firstEchelonArrivalCdf;
    public double[] firstEchelonCdfMetadata;
    public double[][] secondEchelonArrivalCdf;
    public double[] secondEchelonCdfMetadata;
    public double[][] outputArrivalCdf;
    public double[] outputCdfMetadata;
    public double readyTime;
    public double dueTime;
    public LogNormalDistribution travelTimeDistribution;
    public LogNormalDistribution serviceTimeDistribution;

    public String fromNodeId = null;
    public String toNodeId = null;
    public String echelonMode = null;

    public FFunction(
            double[][] previousArrivalCdf,
            double[] previousCdfMetadata,
            double readyTime,
            double dueTime,
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            String fromNodeId,
            String toNodeId,
            String echelonMode) {

        this.previousArrivalCdf = previousArrivalCdf;
        this.previousCdfMetadata = previousCdfMetadata;
        this.readyTime = readyTime;
        this.dueTime = dueTime;

        this.serviceTimeDistribution = serviceTimeDistribution;
        this.travelTimeDistribution = travelTimeDistribution;

        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.echelonMode = echelonMode;
    }

    public FFunction(
            Node previousNode,
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            String fromNodeId,
            String toNodeId,
            String echelonMode) {

        this.firstEchelonArrivalCdf = previousNode.firstEchelonArrivalCdf;
        this.firstEchelonCdfMetadata = previousNode.firstEchelonCdfMetadata;
        this.secondEchelonArrivalCdf = previousNode.secondEchelonArrivalCdf;
        this.secondEchelonCdfMetadata = previousNode.secondEchelonCdfMetadata;

        this.readyTime = 0;
        this.dueTime = ProblemParameters.timeHorizonMinutes;

        this.serviceTimeDistribution = serviceTimeDistribution;
        this.travelTimeDistribution = travelTimeDistribution;

        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.echelonMode = echelonMode;
    }

    public FFunction(LogNormalDistribution travelTimeDistribution) {
        this.travelTimeDistribution = travelTimeDistribution;
        this.readyTime = 0;
    }

    public void calculateIniNode(double departureTime) {
        outputArrivalCdf = new double[ExperimentParameters.cdfGridPoints][2];
        outputCdfMetadata = new double[4];

        // Probability range defined by the configured stochastic quantiles.
        double[] travelBounds = StochPrecomputeCache.getConfiguredQuantileBounds(travelTimeDistribution);
        double travelLowerBound = travelBounds[0];
        double travelUpperBound = travelBounds[1];

        // Step size and intervals
        double gridStep = (travelUpperBound - travelLowerBound) / (ExperimentParameters.cdfGridPoints - 1);
        double[] points = new double[ExperimentParameters.cdfGridPoints];

        for (int i = 0; i < ExperimentParameters.cdfGridPoints; i++) {
            points[i] = travelLowerBound + (i * gridStep);
        }

        // Build the grid and probabilities. CLIPPING keeps
        // the parent CDF and its endpoint masses. Under
        // TRUNCATION, the CDF is conditioned on [qLow,qHigh], so the lower endpoint has
        // CDF 0 and the upper endpoint has CDF 1.
        double lowerQuantile = ExperimentParameters.stochasticLowerQuantile;
        double upperQuantile = ExperimentParameters.stochasticUpperQuantile;
        double probabilityWidth = upperQuantile - lowerQuantile;

        for (int i = 0; i < ExperimentParameters.cdfGridPoints; i++) {
            double point = points[i];
            double probability;

            if (ExperimentParameters.useStochasticTruncation()) {
                if (i == 0) {
                    probability = 0.0;
                } else if (i == ExperimentParameters.cdfGridPoints - 1) {
                    probability = 1.0;
                } else {
                    probability = (travelTimeDistribution.cumulativeProbability(point) - lowerQuantile)
                            / probabilityWidth;
                    probability = Math.max(0.0, Math.min(1.0, probability));
                }
            } else {
                probability = travelTimeDistribution.cumulativeProbability(point);
            }

            outputArrivalCdf[i][0] = point + departureTime;
            outputArrivalCdf[i][1] = probability;
        }

        // Store the output-grid metadata.
        outputCdfMetadata[0] = outputArrivalCdf[0][0];
        outputCdfMetadata[1] = (outputArrivalCdf[(ExperimentParameters.cdfGridPoints - 1)][0] - outputArrivalCdf[0][0]) / (ExperimentParameters.cdfGridPoints - 1);
        outputCdfMetadata[2] = outputArrivalCdf[(ExperimentParameters.cdfGridPoints - 1)][0];
        outputCdfMetadata[3] = ExperimentParameters.cdfGridPoints;
    }

    public void calculateC() {

        // Work only with the active CDF support. This makes the legacy table
        // independent of any spare/uninitialized array capacity and keeps the
        // support calculation consistent with the actual propagated CDF.
        double[][] cleanedPreviousArrivalCdf = CDFCalculator.trimCdfSupport(previousArrivalCdf);
        if (cleanedPreviousArrivalCdf.length == 0) {
            throw new IllegalStateException("Previous arrival CDF is empty.");
        }
        double minimumPreviousArrival = cleanedPreviousArrivalCdf[0][0];
        double maximumPreviousArrival = cleanedPreviousArrivalCdf[cleanedPreviousArrivalCdf.length - 1][0];

        validateQuantileBounds();

        double[] travelBounds = StochPrecomputeCache.getConfiguredQuantileBounds(travelTimeDistribution);
        double travelLowerBound = travelBounds[0];
        double travelUpperBound = travelBounds[1];

        double[] serviceBounds = StochPrecomputeCache.getConfiguredQuantileBounds(serviceTimeDistribution);
        double serviceLowerBound = serviceBounds[0];
        double serviceUpperBound = serviceBounds[1];

        double minimumCombinedDuration = travelLowerBound + serviceLowerBound;
        double maximumCombinedDuration = travelUpperBound + serviceUpperBound;

        double probabilityAtOrBeforeDueTime = findFx(dueTime, cleanedPreviousArrivalCdf);
        double probabilityAfterDueTime = 1.0 - probabilityAtOrBeforeDueTime;

        double minimumOutputArrival;
        if (probabilityAtOrBeforeDueTime > 0.0) {
            minimumOutputArrival = Math.min(
                    Math.max(minimumPreviousArrival, readyTime) + Math.max(minimumCombinedDuration, 0.0),
                    dueTime + travelLowerBound);
        } else {
            minimumOutputArrival = minimumPreviousArrival + travelLowerBound;
        }

        double maximumOutputArrival;
        if (probabilityAfterDueTime > 0.0) {
            maximumOutputArrival = Math.max(
                    Math.min(Math.max(maximumPreviousArrival, readyTime), dueTime) + maximumCombinedDuration,
                    maximumPreviousArrival + travelUpperBound);
        } else {
            maximumOutputArrival = Math.min(Math.max(maximumPreviousArrival, readyTime), dueTime)
                    + maximumCombinedDuration;
        }

        int gridPointCount = ExperimentParameters.cdfGridPoints;
        if (gridPointCount < 2) {
            throw new IllegalArgumentException("cdfGridPoints must be at least 2.");
        }
        double gridStep = (maximumOutputArrival - minimumOutputArrival) / (gridPointCount - 1);
        outputArrivalCdf = new double[gridPointCount][2];
        outputCdfMetadata = new double[4];
        outputCdfMetadata[0] = minimumOutputArrival;
        outputCdfMetadata[1] = gridStep;

        CDFCalculator calculator = new CDFCalculator();
        double[][] travelCdfTable;
        double[][] serviceTravelCdfTable;

        if (hasArcKey()) {
            travelCdfTable = StochPrecomputeCache.getTravelTable(
                    fromNodeId,
                    toNodeId,
                    echelonMode,
                    gridPointCount,
                    calculator,
                    travelTimeDistribution,
                    travelLowerBound,
                    travelUpperBound);

            serviceTravelCdfTable = StochPrecomputeCache.getServiceTravelTable(
                    fromNodeId,
                    toNodeId,
                    echelonMode,
                    gridPointCount,
                    calculator,
                    serviceTimeDistribution,
                    travelTimeDistribution,
                    serviceLowerBound,
                    serviceUpperBound,
                    travelLowerBound,
                    travelUpperBound);
        } else {
            travelCdfTable = calculator.precomputeTravelCdf(
                    travelTimeDistribution, travelLowerBound, travelUpperBound, gridPointCount);
            serviceTravelCdfTable = calculator.precomputeServiceTravelConvolution(
                    serviceTimeDistribution,
                    travelTimeDistribution,
                    serviceLowerBound,
                    serviceUpperBound,
                    travelLowerBound,
                    travelUpperBound,
                    gridPointCount);
        }

        for (int i = 0; i < gridPointCount; i++) {
            outputArrivalCdf[i][0] = i == gridPointCount - 1
                    ? maximumOutputArrival
                    : minimumOutputArrival + i * gridStep;
        }

        calculator.convolveArrivalWithServiceAndTravel(
                cleanedPreviousArrivalCdf,
                readyTime,
                dueTime,
                travelCdfTable,
                serviceTravelCdfTable,
                serviceLowerBound,
                serviceUpperBound,
                travelLowerBound,
                travelUpperBound,
                outputArrivalCdf);
        CDFCalculator.normalizeCdfInPlace(outputArrivalCdf);

        outputCdfMetadata[2] = maximumOutputArrival;
        outputCdfMetadata[3] = gridPointCount;
    }

    public void calculateM() {

        firstEchelonArrivalCdf = CDFCalculator.trimCdfSupport(firstEchelonArrivalCdf);
        secondEchelonArrivalCdf = CDFCalculator.trimCdfSupport(secondEchelonArrivalCdf);
        if (firstEchelonArrivalCdf.length == 0 || secondEchelonArrivalCdf.length == 0) {
            throw new IllegalStateException("Meeting-point arrival CDF is empty.");
        }

        double minimumFirstEchelonArrival = firstEchelonArrivalCdf[0][0];
        double maximumFirstEchelonArrival = firstEchelonArrivalCdf[firstEchelonArrivalCdf.length - 1][0];
        double minimumSecondEchelonArrival = secondEchelonArrivalCdf[0][0];
        double maximumSecondEchelonArrival = secondEchelonArrivalCdf[secondEchelonArrivalCdf.length - 1][0];

        double minimumMeetingArrival = Math.max(minimumFirstEchelonArrival, minimumSecondEchelonArrival);
        double maximumMeetingArrival = Math.max(maximumFirstEchelonArrival, maximumSecondEchelonArrival);

        int gridPointCount = ExperimentParameters.cdfGridPoints;
        if (gridPointCount < 2) {
            throw new IllegalArgumentException("cdfGridPoints must be at least 2.");
        }
        double meetingGridStep = (maximumMeetingArrival - minimumMeetingArrival) / (gridPointCount - 1);

        previousArrivalCdf = new double[gridPointCount][2];
        previousCdfMetadata = new double[4];
        previousCdfMetadata[0] = minimumMeetingArrival;
        previousCdfMetadata[1] = meetingGridStep;

        for (int i = 0; i < gridPointCount; i++) {
            double time = i == gridPointCount - 1
                    ? maximumMeetingArrival
                    : minimumMeetingArrival + i * meetingGridStep;
            previousArrivalCdf[i][0] = time;
            previousArrivalCdf[i][1] = findFx(time, firstEchelonArrivalCdf)
                    * findFx(time, secondEchelonArrivalCdf);
        }
        CDFCalculator.normalizeCdfInPlace(previousArrivalCdf);
        previousCdfMetadata[2] = maximumMeetingArrival;
        previousCdfMetadata[3] = gridPointCount;

        validateQuantileBounds();

        double[] travelBounds = StochPrecomputeCache.getConfiguredQuantileBounds(travelTimeDistribution);
        double travelLowerBound = travelBounds[0];
        double travelUpperBound = travelBounds[1];

        double[] serviceBounds = StochPrecomputeCache.getConfiguredQuantileBounds(serviceTimeDistribution);
        double serviceLowerBound = serviceBounds[0];
        double serviceUpperBound = serviceBounds[1];

        double minimumCombinedDuration = travelLowerBound + serviceLowerBound;
        double maximumCombinedDuration = travelUpperBound + serviceUpperBound;

        double minimumOutputArrival = minimumMeetingArrival + Math.max(minimumCombinedDuration, 0.0);
        double maximumOutputArrival = maximumMeetingArrival + maximumCombinedDuration;
        double outputGridStep = (maximumOutputArrival - minimumOutputArrival) / (gridPointCount - 1);

        outputArrivalCdf = new double[gridPointCount][2];
        outputCdfMetadata = new double[4];
        outputCdfMetadata[0] = minimumOutputArrival;
        outputCdfMetadata[1] = outputGridStep;

        CDFCalculator calculator = new CDFCalculator();
        double[][] travelCdfTable;
        double[][] serviceTravelCdfTable;

        if (hasArcKey()) {
            travelCdfTable = StochPrecomputeCache.getTravelTable(
                    fromNodeId,
                    toNodeId,
                    echelonMode,
                    gridPointCount,
                    calculator,
                    travelTimeDistribution,
                    travelLowerBound,
                    travelUpperBound);
            serviceTravelCdfTable = StochPrecomputeCache.getServiceTravelTable(
                    fromNodeId,
                    toNodeId,
                    echelonMode,
                    gridPointCount,
                    calculator,
                    serviceTimeDistribution,
                    travelTimeDistribution,
                    serviceLowerBound,
                    serviceUpperBound,
                    travelLowerBound,
                    travelUpperBound);
        } else {
            serviceTravelCdfTable = calculator.precomputeServiceTravelConvolution(
                    serviceTimeDistribution,
                    travelTimeDistribution,
                    serviceLowerBound,
                    serviceUpperBound,
                    travelLowerBound,
                    travelUpperBound,
                    gridPointCount);
            travelCdfTable = calculator.precomputeTravelCdf(
                    travelTimeDistribution, travelLowerBound, travelUpperBound, gridPointCount);
        }

        for (int i = 0; i < gridPointCount; i++) {
            outputArrivalCdf[i][0] = i == gridPointCount - 1
                    ? maximumOutputArrival
                    : minimumOutputArrival + i * outputGridStep;
        }

        calculator.convolveArrivalWithServiceAndTravel(
                previousArrivalCdf,
                readyTime,
                dueTime,
                travelCdfTable,
                serviceTravelCdfTable,
                serviceLowerBound,
                serviceUpperBound,
                travelLowerBound,
                travelUpperBound,
                outputArrivalCdf);
        CDFCalculator.normalizeCdfInPlace(outputArrivalCdf);

        outputCdfMetadata[2] = maximumOutputArrival;
        outputCdfMetadata[3] = gridPointCount;
    }

    public static double findFx(double value, double[][] cdf) {
        if (cdf.length == 0 || cdf[0].length != 2) {
            throw new IllegalArgumentException("Invalid CDF array. It must have two columns.");
        }

        // Below the lower support the CDF is zero. At the lower support
        // itself, fall through to interpolation so the stored endpoint point mass (when present, e.g. under CLIPPING)
        // is preserved.
        if (value < cdf[0][0]) {
            return 0;
        }

        // At or above the upper support, the CDF is one.
        if (value >= cdf[cdf.length - 1][0]) {
            return 1;
        }

        for (int i = 0; i < cdf.length - 1; i++) {
            double lowerSupportPoint = cdf[i][0];
            double upperSupportPoint = cdf[i + 1][0];
            double lowerCdfValue = cdf[i][1];
            double upperCdfValue = cdf[i + 1][1];

            if (value >= lowerSupportPoint && value <= upperSupportPoint) {
                // Linear interpolation between adjacent support points.
                return lowerCdfValue + (upperCdfValue - lowerCdfValue) * (value - lowerSupportPoint) / (upperSupportPoint - lowerSupportPoint);
            }
        }

        // The range checks above should cover every valid value.
        throw new IllegalStateException("Value not found in CDF range.");
    }

    private boolean hasArcKey() {
        return fromNodeId != null && toNodeId != null && echelonMode != null;
    }


    private void validateQuantileBounds() {
        double lowerQuantile = ExperimentParameters.stochasticLowerQuantile;
        double upperQuantile = ExperimentParameters.stochasticUpperQuantile;

        if (lowerQuantile <= 0.0 || upperQuantile >= 1.0 || lowerQuantile >= upperQuantile) {
            throw new IllegalArgumentException(
            		"Invalid stochastic quantile bounds. Use values such as 0.05 and 0.95, or 0.01 and 0.99. Do not use 0 and 1."
            );
        }
    }
}

