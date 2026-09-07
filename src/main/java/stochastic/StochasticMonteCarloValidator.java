package stochastic;

import config.ExperimentParameters;
import config.ProblemParameters;
import problem.Customer;
import solution.Solution;

/**
 * Independent Monte Carlo comparison for a stochastic search solution.
 *
 * The validator uses the simulation engine directly and does not write failure
 * probabilities back into the search solution. Monte Carlo validation therefore
 * does not modify the search objective, feasibility, departures, or acceptance decisions.
 */
public final class StochasticMonteCarloValidator {

    private StochasticMonteCarloValidator() {
    }

    public static StochasticMonteCarloResult validate(Solution solution) {
        if (solution == null || solution.getRunContext() == null) {
            throw new IllegalArgumentException(
                    "Monte Carlo validation requires a run-owned solution.");
        }
        return validate(
                solution,
                ExperimentParameters.finalSimulationReplications,
                solution.getRunContext().getFinalSimulationValidationSeed());
    }

    /**
     * Runs an independent Monte Carlo comparison without modifying the supplied
     * search solution, using an explicitly supplied replication count and seed.
     */
    public static StochasticMonteCarloResult validate(
            Solution solution,
            int replications,
            long simulationSeed) {
        if (solution == null || solution.getRunContext() == null) {
            throw new IllegalArgumentException(
                    "Monte Carlo validation requires a run-owned solution.");
        }
        if (replications <= 0) {
            throw new IllegalArgumentException("Monte Carlo replications must be positive.");
        }

        long startNanos = System.nanoTime();
        try {
            Simulator simulator = new Simulator(solution, false);
            simulator.run(replications, simulationSeed);

            int customerCount = solution.secondEchelonCustomers.getCustomerCount();
            if (customerCount == 0) {
                return StochasticMonteCarloResult.success(
                        replications,
                        0.0,
                        100.0,
                        100.0,
                        0,
                        0.0,
                        0.0,
                        elapsedMillis(startNanos));
            }

            double expectedFailureCost = 0.0;
            double successSum = 0.0;
            double minimumSuccess = 1.0;
            int belowRequired = 0;
            double absoluteErrorSum = 0.0;
            double maximumAbsoluteError = 0.0;
            int comparedCustomers = 0;

            for (Customer customer : solution.secondEchelonCustomers.customers) {
                double failureProbability = simulator.getSecondFailureCount(customer)
                        / (double) replications;
                double successProbability = 1.0 - failureProbability;

                expectedFailureCost += customer.unitRecourseCost * failureProbability;
                successSum += successProbability;
                minimumSuccess = Math.min(minimumSuccess, successProbability);
                if (failureProbability > ProblemParameters.failureProbability) {
                    belowRequired++;
                }

                double searchFailureProbability = customer.secondEchelonFailureProbability;
                if (Double.isFinite(searchFailureProbability)) {
                    double error = Math.abs(
                            failureProbability - searchFailureProbability);
                    absoluteErrorSum += error;
                    maximumAbsoluteError = Math.max(maximumAbsoluteError, error);
                    comparedCustomers++;
                }
            }

            return StochasticMonteCarloResult.success(
                    replications,
                    expectedFailureCost,
                    100.0 * successSum / customerCount,
                    100.0 * minimumSuccess,
                    belowRequired,
                    comparedCustomers == 0
                            ? Double.NaN
                            : absoluteErrorSum / comparedCustomers,
                    comparedCustomers == 0
                            ? Double.NaN
                            : maximumAbsoluteError,
                    elapsedMillis(startNanos));
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = exception.getClass().getSimpleName();
            }
            return StochasticMonteCarloResult.failure(
                    replications,
                    message,
                    elapsedMillis(startNanos));
        }
    }

    private static long elapsedMillis(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000_000.0);
    }
}
