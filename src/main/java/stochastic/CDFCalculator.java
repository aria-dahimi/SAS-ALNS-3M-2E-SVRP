package stochastic;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import config.ExperimentParameters;

public class CDFCalculator {

    //
    // The stored previous-arrival CDF may contain probability mass at either
    // support endpoint. Under the original CLIPPING strategy this is present
    // from the beginning; under TRUNCATION there is no initial tail mass, but
    // waiting/time-window operations can still create endpoint point masses.
    public void convolveArrivalWithServiceAndTravel(double[][] previousArrivalCdf, double readyTime, double dueTime, double[][] travelCdfTable, double[][] serviceTravelCdfTable,
            double serviceLowerBound, double serviceUpperBound, double travelLowerBound, double travelUpperBound, double[][] outputArrivalCdf) {

        if (previousArrivalCdf == null || previousArrivalCdf.length == 0) {
            return;
        }

        // Any probability mass stored at the lower support point.
        double lowerEndpointMass = clampProbability(previousArrivalCdf[0][1]);
        addPreviousArrivalMass(
                previousArrivalCdf[0][0],
                lowerEndpointMass,
                readyTime,
                dueTime,
                travelCdfTable,
                serviceTravelCdfTable,
                outputArrivalCdf
        );

        // Interior probability mass between consecutive CDF support points.
        for (int i = 0; i < previousArrivalCdf.length - 1; i++) {
            double intervalStart = previousArrivalCdf[i][0];
            double intervalEnd = previousArrivalCdf[i + 1][0];
            double intervalProbability = previousArrivalCdf[i + 1][1] - previousArrivalCdf[i][1];

            int subIntervalCount = ExperimentParameters.previousArrivalSubIntervals;
            double subIntervalWidth = (intervalEnd - intervalStart) / subIntervalCount;

            for (int j = 0; j < subIntervalCount; j++) {
                double midpointArrival = intervalStart + (j + 0.5) * subIntervalWidth;
                double weight = intervalProbability / subIntervalCount;

                addPreviousArrivalMass(
                        midpointArrival,
                        weight,
                        readyTime,
                        dueTime,
                        travelCdfTable,
                        serviceTravelCdfTable,
                        outputArrivalCdf
                );
            }
        }

        // Any probability mass stored at the upper support point.
        int lastIndex = previousArrivalCdf.length - 1;
        double upperEndpointMass = clampProbability(1.0 - previousArrivalCdf[lastIndex][1]);
        addPreviousArrivalMass(
                previousArrivalCdf[lastIndex][0],
                upperEndpointMass,
                readyTime,
                dueTime,
                travelCdfTable,
                serviceTravelCdfTable,
                outputArrivalCdf
        );
    }

    private void addPreviousArrivalMass(
            double arrivalTime,
            double weight,
            double readyTime,
            double dueTime,
            double[][] travelCdfTable,
            double[][] serviceTravelCdfTable,
            double[][] outputArrivalCdf) {

        if (weight <= 0.0) {
            return;
        }

        // Waiting at the earliest time window clips the previous arrival from below.
        // This operation is independent of the stochastic tail strategy.
        double effectiveArrival = Math.max(arrivalTime, readyTime);

        for (int k = 0; k < outputArrivalCdf.length; k++) {
            double remainingDuration = outputArrivalCdf[k][0] - effectiveArrival;

            if (effectiveArrival <= dueTime) {
                double serviceTravelCdfValue = FFunction.findFx(remainingDuration, serviceTravelCdfTable);
                outputArrivalCdf[k][1] += weight * serviceTravelCdfValue;
            } else {
                double travelCdfValue = FFunction.findFx(remainingDuration, travelCdfTable);
                outputArrivalCdf[k][1] += weight * travelCdfValue;
            }
        }
    }

    private double clampProbability(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public double[][] precomputeServiceTravelConvolution(
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            double serviceLowerBound, double serviceUpperBound, double travelLowerBound, double travelUpperBound, int gridPointCount) {

        if (ExperimentParameters.useStochasticTruncation()) {
            return precomputeServiceTravelConvolutionTruncation(
                    serviceTimeDistribution,
                    travelTimeDistribution,
                    serviceLowerBound,
                    serviceUpperBound,
                    travelLowerBound,
                    travelUpperBound,
                    gridPointCount
            );
        }

        return precomputeServiceTravelConvolutionClipping(
                serviceTimeDistribution,
                travelTimeDistribution,
                serviceLowerBound,
                serviceUpperBound,
                travelLowerBound,
                travelUpperBound,
                gridPointCount
        );
    }

    // Clipped service and travel distributions.
    // Tail mass is retained and placed at the configured quantile endpoints.
    private double[][] precomputeServiceTravelConvolutionClipping(
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            double serviceLowerBound, double serviceUpperBound, double travelLowerBound, double travelUpperBound, int gridPointCount) {

        double lowerTailMass = ExperimentParameters.stochasticLowerQuantile;
        double upperTailMass = 1.0 - ExperimentParameters.stochasticUpperQuantile;

        double minimumCombinedDuration = serviceLowerBound + travelLowerBound;
        double maximumCombinedDuration = serviceUpperBound + travelUpperBound;
        double gridStep = (maximumCombinedDuration - minimumCombinedDuration) / (gridPointCount - 1);

        double[][] serviceTravelCdf = new double[gridPointCount][2];

        int serviceIntervalCount = gridPointCount;
        validateGridPointCount(gridPointCount);

        double serviceIntervalWidth = (serviceUpperBound - serviceLowerBound) / serviceIntervalCount;

        double[] middleServiceWeights = new double[serviceIntervalCount];
        double rawMiddleMass = 0.0;
        for (int j = 0; j < serviceIntervalCount; j++) {
            double serviceIntervalStart = serviceLowerBound + j * serviceIntervalWidth;
            double serviceIntervalEnd = serviceLowerBound + (j + 1.0) * serviceIntervalWidth;
            double intervalMass = serviceTimeDistribution.cumulativeProbability(serviceIntervalEnd)
                    - serviceTimeDistribution.cumulativeProbability(serviceIntervalStart);
            intervalMass = Math.max(0.0, intervalMass);
            middleServiceWeights[j] = intervalMass;
            rawMiddleMass += intervalMass;
        }

        double targetMiddleMass = ExperimentParameters.stochasticUpperQuantile
                - ExperimentParameters.stochasticLowerQuantile;
        if (!(rawMiddleMass > 0.0) || !Double.isFinite(rawMiddleMass)) {
            throw new IllegalStateException("Invalid numerical service-time mass in stochastic convolution.");
        }
        // Tiny rescaling removes only floating-point/quantile inversion drift.
        double middleMassScale = targetMiddleMass / rawMiddleMass;

        for (int i = 0; i < gridPointCount; i++) {
            double combinedDuration = minimumCombinedDuration + i * gridStep;
            double cdfValue = 0.0;

            // Lower service-time tail: all S < serviceLowerBound is represented at serviceLowerBound.
            cdfValue += lowerTailMass * clippedTravelCdf(
                    combinedDuration - serviceLowerBound,
                    travelTimeDistribution,
                    travelLowerBound,
                    travelUpperBound
            );

            // Middle service-time range: serviceLowerBound <= S <= serviceUpperBound.
            for (int j = 0; j < serviceIntervalCount; j++) {
                double representativeServiceDuration = serviceLowerBound + (j + 0.5) * serviceIntervalWidth;
                double serviceIntervalProbability = middleServiceWeights[j] * middleMassScale;

                cdfValue += serviceIntervalProbability * clippedTravelCdf(
                        combinedDuration - representativeServiceDuration,
                        travelTimeDistribution,
                        travelLowerBound,
                        travelUpperBound
                );
            }

            // Upper service-time tail: all S > serviceUpperBound is represented at serviceUpperBound.
            cdfValue += upperTailMass * clippedTravelCdf(
                    combinedDuration - serviceUpperBound,
                    travelTimeDistribution,
                    travelLowerBound,
                    travelUpperBound
            );

            // Exact endpoint masses for the clipped sum.
            if (i == 0) {
                cdfValue = lowerTailMass * ExperimentParameters.stochasticLowerQuantile;
            } else if (i == gridPointCount - 1) {
                cdfValue = 1.0;
            }

            cdfValue = clampProbability(cdfValue);
            if (i > 0) {
                cdfValue = Math.max(cdfValue, serviceTravelCdf[i - 1][1]);
            }

            serviceTravelCdf[i][0] = combinedDuration;
            serviceTravelCdf[i][1] = cdfValue;
        }

        return serviceTravelCdf;
    }

    // True truncation: values outside [qLow,qHigh] are removed and the
    // remaining probability is renormalized to one. No tail probability is
    // accumulated at serviceLowerBound/serviceUpperBound or travelLowerBound/travelUpperBound.
    private double[][] precomputeServiceTravelConvolutionTruncation(
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            double serviceLowerBound, double serviceUpperBound, double travelLowerBound, double travelUpperBound, int gridPointCount) {

        double minimumCombinedDuration = serviceLowerBound + travelLowerBound;
        double maximumCombinedDuration = serviceUpperBound + travelUpperBound;
        double gridStep = (maximumCombinedDuration - minimumCombinedDuration) / (gridPointCount - 1);

        double[][] serviceTravelCdf = new double[gridPointCount][2];

        int serviceIntervalCount = gridPointCount;
        validateGridPointCount(gridPointCount);

        double serviceIntervalWidth = (serviceUpperBound - serviceLowerBound) / serviceIntervalCount;

        // Exact parent-distribution mass of each service interval, then
        // renormalize the retained [serviceLowerBound,serviceUpperBound] mass to one.
        double[] serviceWeights = new double[serviceIntervalCount];
        double retainedServiceMass = 0.0;
        for (int j = 0; j < serviceIntervalCount; j++) {
            double serviceIntervalStart = serviceLowerBound + j * serviceIntervalWidth;
            double serviceIntervalEnd = serviceLowerBound + (j + 1.0) * serviceIntervalWidth;
            double intervalMass = serviceTimeDistribution.cumulativeProbability(serviceIntervalEnd)
                    - serviceTimeDistribution.cumulativeProbability(serviceIntervalStart);
            intervalMass = Math.max(0.0, intervalMass);
            serviceWeights[j] = intervalMass;
            retainedServiceMass += intervalMass;
        }

        if (!(retainedServiceMass > 0.0) || !Double.isFinite(retainedServiceMass)) {
            throw new IllegalStateException("Invalid numerical service-time mass in truncated stochastic convolution.");
        }
        double serviceMassScale = 1.0 / retainedServiceMass;

        for (int i = 0; i < gridPointCount; i++) {
            double combinedDuration = minimumCombinedDuration + i * gridStep;
            double cdfValue = 0.0;

            for (int j = 0; j < serviceIntervalCount; j++) {
                double representativeServiceDuration = serviceLowerBound + (j + 0.5) * serviceIntervalWidth;
                double serviceIntervalProbability = serviceWeights[j] * serviceMassScale;

                cdfValue += serviceIntervalProbability * truncatedTravelCdf(
                        combinedDuration - representativeServiceDuration,
                        travelTimeDistribution,
                        travelLowerBound,
                        travelUpperBound
                );
            }

            // The conditionally truncated sum has no lower endpoint atom and
            // all retained probability lies at or below the upper endpoint.
            if (i == 0) {
                cdfValue = 0.0;
            } else if (i == gridPointCount - 1) {
                cdfValue = 1.0;
            }

            cdfValue = clampProbability(cdfValue);
            if (i > 0) {
                cdfValue = Math.max(cdfValue, serviceTravelCdf[i - 1][1]);
            }

            serviceTravelCdf[i][0] = combinedDuration;
            serviceTravelCdf[i][1] = cdfValue;
        }

        return serviceTravelCdf;
    }

    public double[][] precomputeTravelCdf(
            LogNormalDistribution travelTimeDistribution,
            double travelLowerBound, double travelUpperBound, int gridPointCount) {

        double[][] travelCdf = new double[gridPointCount][2];
        double gridStep = (travelUpperBound - travelLowerBound) / (gridPointCount - 1);

        for (int i = 0; i < gridPointCount; i++) {
            double travelDuration = travelLowerBound + i * gridStep;
            double cdfValue;

            if (ExperimentParameters.useStochasticTruncation()) {
                cdfValue = truncatedTravelCdf(travelDuration, travelTimeDistribution, travelLowerBound, travelUpperBound);
            } else {
                cdfValue = clippedTravelCdf(travelDuration, travelTimeDistribution, travelLowerBound, travelUpperBound);
            }

            // Exact endpoints prevent quantile-inversion roundoff from changing
            // the intended boundary behavior.
            if (i == 0) {
                cdfValue = ExperimentParameters.useStochasticTruncation()
                        ? 0.0
                        : ExperimentParameters.stochasticLowerQuantile;
            } else if (i == gridPointCount - 1) {
                cdfValue = 1.0;
            }

            travelCdf[i][0] = travelDuration;
            travelCdf[i][1] = clampProbability(cdfValue);
        }
        return travelCdf;
    }

    /**
     * Enforces the mathematical invariants of a bounded CDF after numerical
     * convolution: finite probabilities, [0,1] range, non-decreasing values,
     * and unit mass at the explicitly constructed upper support.
     */
    public static void normalizeCdfInPlace(double[][] cdf) {
        if (cdf == null || cdf.length == 0) {
            throw new IllegalArgumentException("CDF cannot be empty.");
        }
        double previous = 0.0;
        for (int i = 0; i < cdf.length; i++) {
            double probability = cdf[i][1];
            if (!Double.isFinite(probability)) {
                throw new IllegalArgumentException("CDF contains a non-finite probability.");
            }
            probability = Math.max(0.0, Math.min(1.0, probability));
            if (probability < previous) {
                probability = previous;
            }
            cdf[i][1] = probability;
            previous = probability;
        }
        cdf[cdf.length - 1][1] = 1.0;
    }

    public static double[][] trimCdfSupport(double[][] previousArrivalCdf) {
        int lastNonZeroIndex = -1;

        for (int i = previousArrivalCdf.length - 1; i >= 0; i--) {
            if (previousArrivalCdf[i][1] != 0.0) {
                lastNonZeroIndex = i;
                break;
            }
        }

        if (lastNonZeroIndex == -1) {
            return new double[0][0];
        }

        double[][] cleanedCdf = new double[lastNonZeroIndex + 1][2];
        System.arraycopy(previousArrivalCdf, 0, cleanedCdf, 0, lastNonZeroIndex + 1);

        return cleanedCdf;
    }

    private double clippedTravelCdf(
            double travelDuration,
            LogNormalDistribution travelTimeDistribution,
            double travelLowerBound,
            double travelUpperBound) {

        if (travelDuration < travelLowerBound) {
            return 0.0;
        } else if (travelDuration >= travelUpperBound) {
            return 1.0;
        }
        return clampProbability(travelTimeDistribution.cumulativeProbability(travelDuration));
    }

    private double truncatedTravelCdf(
            double travelDuration,
            LogNormalDistribution travelTimeDistribution,
            double travelLowerBound,
            double travelUpperBound) {

        if (travelDuration <= travelLowerBound) {
            return 0.0;
        } else if (travelDuration >= travelUpperBound) {
            return 1.0;
        }

        double lowerQuantile = ExperimentParameters.stochasticLowerQuantile;
        double upperQuantile = ExperimentParameters.stochasticUpperQuantile;
        double retainedProbabilityMass = upperQuantile - lowerQuantile;
        double parentCumulativeProbability = travelTimeDistribution.cumulativeProbability(travelDuration);
        return clampProbability((parentCumulativeProbability - lowerQuantile) / retainedProbabilityMass);
    }

    private void validateGridPointCount(int gridPointCount) {
        if (gridPointCount <= 0) {
            throw new IllegalArgumentException("gridPointCount must be positive.");
        }
    }
}
