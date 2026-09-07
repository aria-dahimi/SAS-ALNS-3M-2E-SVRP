package experiment;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Small statistical helpers used by the experiment reports. */
final class ReportStatistics {

    private ReportStatistics() {
    }

    static double mean(List<RunResult> results, ToDoubleFunction<RunResult> metric) {
        if (results.isEmpty()) {
            return Double.NaN;
        }
        double total = 0.0;
        for (RunResult result : results) {
            total += metric.applyAsDouble(result);
        }
        return total / results.size();
    }

    static double min(List<RunResult> results, ToDoubleFunction<RunResult> metric) {
        if (results.isEmpty()) {
            return Double.NaN;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (RunResult result : results) {
            minimum = Math.min(minimum, metric.applyAsDouble(result));
        }
        return minimum;
    }

    // Sample CV in percent; the standard deviation uses n-1.
    static double coefficientOfVariation(
            List<RunResult> results,
            ToDoubleFunction<RunResult> metric) {
        if (results.isEmpty()) {
            return Double.NaN;
        }
        double mean = mean(results, metric);
        if (!Double.isFinite(mean)) {
            return Double.NaN;
        }
        if (mean == 0.0 || results.size() == 1) {
            return 0.0;
        }

        double squaredDeviationSum = 0.0;
        for (RunResult result : results) {
            double value = metric.applyAsDouble(result);
            if (!Double.isFinite(value)) {
                return Double.NaN;
            }
            double deviation = value - mean;
            squaredDeviationSum += deviation * deviation;
        }
        double standardDeviation = Math.sqrt(
                squaredDeviationSum / (results.size() - 1));
        return standardDeviation / Math.abs(mean) * 100.0;
    }

    static double stochasticRuntimeSharePercent(List<RunResult> results) {
        double runtime = mean(results, RunResult::getRuntimeSeconds);
        double stochasticTime = mean(results, RunResult::getStochasticEvaluationTimeSeconds);
        if (!Double.isFinite(runtime) || runtime <= 0.0
                || !Double.isFinite(stochasticTime)) {
            return Double.NaN;
        }
        return stochasticTime / runtime * 100.0;
    }

    static double improvementPercent(double initialObjective, double bestObjective) {
        if (!Double.isFinite(initialObjective)
                || !Double.isFinite(bestObjective)
                || initialObjective == 0.0) {
            return Double.NaN;
        }
        return (initialObjective - bestObjective) / Math.abs(initialObjective) * 100.0;
    }
}
