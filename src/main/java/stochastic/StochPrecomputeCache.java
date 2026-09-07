package stochastic;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import config.ExperimentParameters;

/**
 * Reuses stochastic transition tables and distribution quantile bounds within
 * one problem instance. Cache contents are cleared at instance boundaries.
 */
public final class StochPrecomputeCache {

    private static final Map<String, double[][]> travelTableCache = new HashMap<>();
    private static final Map<String, double[][]> serviceTravelTableCache = new HashMap<>();

    /* Cache configured quantile endpoints for each immutable log-normal distribution. */
    private static final IdentityHashMap<LogNormalDistribution, double[]> quantileBoundsCache =
            new IdentityHashMap<>();
    private static double cachedLowerQuantile = Double.NaN;
    private static double cachedUpperQuantile = Double.NaN;

    private static int travelHits = 0;
    private static int travelMisses = 0;
    private static int serviceTravelHits = 0;
    private static int serviceTravelMisses = 0;

    /*
     * The current search evaluates candidates sequentially. Keeping the owner
     * lets us fail clearly if a future change accidentally accesses the shared
     * cache concurrently while an instance is active.
     */
    private static volatile String activeInstanceId = null;
    private static volatile Thread activeInstanceThread = null;

    private StochPrecomputeCache() {
    }

    /**
     * Starts the cache lifetime for one benchmark instance. Values from any
     * previously processed instance are discarded before the new instance is read.
     */
    public static synchronized void beginInstance(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Instance id cannot be null or blank.");
        }
        if (activeInstanceId != null) {
            throw new IllegalStateException(
                    "Stochastic cache is already active for instance " + activeInstanceId + ".");
        }

        clearCacheContents();
        activeInstanceId = instanceId;
        activeInstanceThread = Thread.currentThread();
    }

    /**
     * Ends one benchmark instance and discards every cached stochastic value.
     * This is intentionally called from a finally block by the batch runner.
     */
    public static synchronized void endInstance(String instanceId) {
        if (activeInstanceId != null
                && instanceId != null
                && !activeInstanceId.equals(instanceId)) {
            throw new IllegalStateException(
                    "Trying to close stochastic cache for instance " + instanceId
                            + " while " + activeInstanceId + " is active.");
        }

        clearCacheContents();
        activeInstanceId = null;
        activeInstanceThread = null;
    }

    /**
     * Clears cached values while retaining the current instance ownership.
     * Existing initialization code may call this safely within an instance.
     */
    public static synchronized void clear() {
        checkSequentialAccess();
        clearCacheContents();
    }

    private static void clearCacheContents() {
        travelTableCache.clear();
        serviceTravelTableCache.clear();
        quantileBoundsCache.clear();
        cachedLowerQuantile = Double.NaN;
        cachedUpperQuantile = Double.NaN;

        travelHits = 0;
        travelMisses = 0;
        serviceTravelHits = 0;
        serviceTravelMisses = 0;
    }

    private static void checkSequentialAccess() {
        if (activeInstanceThread != null
                && activeInstanceThread != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Stochastic precompute cache is instance-scoped and single-threaded. "
                            + "Active instance: " + activeInstanceId + ".");
        }
    }

    public static double[] getConfiguredQuantileBounds(
            LogNormalDistribution distribution) {
        checkSequentialAccess();

        double lowerQuantile = ExperimentParameters.stochasticLowerQuantile;
        double upperQuantile = ExperimentParameters.stochasticUpperQuantile;
        if (Double.compare(lowerQuantile, cachedLowerQuantile) != 0
                || Double.compare(upperQuantile, cachedUpperQuantile) != 0) {
            quantileBoundsCache.clear();
            cachedLowerQuantile = lowerQuantile;
            cachedUpperQuantile = upperQuantile;
        }

        double[] quantileBounds = quantileBoundsCache.get(distribution);
        if (quantileBounds == null) {
            quantileBounds = new double[] {
                    distribution.inverseCumulativeProbability(lowerQuantile),
                    distribution.inverseCumulativeProbability(upperQuantile)
            };
            quantileBoundsCache.put(distribution, quantileBounds);
        }
        return quantileBounds;
    }

    public static double[][] getTravelTable(
            String fromNodeId,
            String toNodeId,
            String echelonMode,
            int gridPointCount,
            CDFCalculator calculator,
            LogNormalDistribution travelTimeDistribution,
            double travelLowerBound,
            double travelUpperBound) {
        checkSequentialAccess();

        String cacheKey = buildTravelKey(
                fromNodeId,
                toNodeId,
                echelonMode,
                gridPointCount,
                travelTimeDistribution,
                travelLowerBound,
                travelUpperBound);
        double[][] cachedTable = travelTableCache.get(cacheKey);

        if (cachedTable == null) {
            cachedTable = calculator.precomputeTravelCdf(
                    travelTimeDistribution,
                    travelLowerBound,
                    travelUpperBound,
                    gridPointCount);
            travelTableCache.put(cacheKey, cachedTable);
            travelMisses++;
        } else {
            travelHits++;
        }

        return cachedTable;
    }

    public static double[][] getServiceTravelTable(
            String fromNodeId,
            String toNodeId,
            String echelonMode,
            int gridPointCount,
            CDFCalculator calculator,
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            double serviceLowerBound,
            double serviceUpperBound,
            double travelLowerBound,
            double travelUpperBound) {
        checkSequentialAccess();

        String cacheKey = buildServiceTravelKey(
                fromNodeId,
                toNodeId,
                echelonMode,
                gridPointCount,
                serviceTimeDistribution,
                travelTimeDistribution,
                serviceLowerBound,
                serviceUpperBound,
                travelLowerBound,
                travelUpperBound);
        double[][] cachedTable = serviceTravelTableCache.get(cacheKey);

        if (cachedTable == null) {
            cachedTable = calculator.precomputeServiceTravelConvolution(
                    serviceTimeDistribution,
                    travelTimeDistribution,
                    serviceLowerBound,
                    serviceUpperBound,
                    travelLowerBound,
                    travelUpperBound,
                    gridPointCount);
            serviceTravelTableCache.put(cacheKey, cachedTable);
            serviceTravelMisses++;
        } else {
            serviceTravelHits++;
        }

        return cachedTable;
    }

    private static String buildTravelKey(
            String fromNodeId,
            String toNodeId,
            String echelonMode,
            int gridPointCount,
            LogNormalDistribution travelTimeDistribution,
            double travelLowerBound,
            double travelUpperBound) {
        return "A_"
                + commonKeyPrefix(fromNodeId, toNodeId, echelonMode, gridPointCount)
                + "_td" + distributionKey(travelTimeDistribution)
                + "_tb" + doubleKey(travelLowerBound) + ":" + doubleKey(travelUpperBound);
    }

    private static String buildServiceTravelKey(
            String fromNodeId,
            String toNodeId,
            String echelonMode,
            int gridPointCount,
            LogNormalDistribution serviceTimeDistribution,
            LogNormalDistribution travelTimeDistribution,
            double serviceLowerBound,
            double serviceUpperBound,
            double travelLowerBound,
            double travelUpperBound) {
        return "SA_"
                + commonKeyPrefix(fromNodeId, toNodeId, echelonMode, gridPointCount)
                + "_sd" + distributionKey(serviceTimeDistribution)
                + "_sb" + doubleKey(serviceLowerBound) + ":" + doubleKey(serviceUpperBound)
                + "_td" + distributionKey(travelTimeDistribution)
                + "_tb" + doubleKey(travelLowerBound) + ":" + doubleKey(travelUpperBound);
    }

    private static String commonKeyPrefix(
            String fromNodeId,
            String toNodeId,
            String echelonMode,
            int gridPointCount) {
        return ExperimentParameters.stochasticTailStrategy
                + "_q" + doubleKey(ExperimentParameters.stochasticLowerQuantile)
                + ":" + doubleKey(ExperimentParameters.stochasticUpperQuantile)
                + "_" + fromNodeId + "_" + toNodeId
                + "_" + echelonMode + "_n" + gridPointCount;
    }

    private static String distributionKey(LogNormalDistribution distribution) {
        return doubleKey(distribution.getScale()) + ":" + doubleKey(distribution.getShape());
    }

    private static String doubleKey(double value) {
        return Double.toHexString(value);
    }

    public static void printStats(String label) {
        checkSequentialAccess();

        int travelLookups = travelHits + travelMisses;
        int serviceTravelLookups = serviceTravelHits + serviceTravelMisses;
        double travelHitRate = travelLookups == 0 ? 0.0
                : 100.0 * travelHits / travelLookups;
        double serviceTravelHitRate = serviceTravelLookups == 0 ? 0.0
                : 100.0 * serviceTravelHits / serviceTravelLookups;

        System.out.println("========== Stochastic Precompute Cache: " + label + " ==========");
        System.out.println("Instance scope: "
                + (activeInstanceId == null ? "standalone/unscoped" : activeInstanceId));
        System.out.println("Travel table size: " + travelTableCache.size());
        System.out.println("Service+travel table size: " + serviceTravelTableCache.size());
        System.out.println("Travel hits: " + travelHits
                + ", misses: " + travelMisses
                + ", hit rate: " + String.format("%.2f", travelHitRate) + "%");
        System.out.println("Service+travel hits: " + serviceTravelHits
                + ", misses: " + serviceTravelMisses
                + ", hit rate: " + String.format("%.2f", serviceTravelHitRate) + "%");
        System.out.println("===============================================================");
    }
}
