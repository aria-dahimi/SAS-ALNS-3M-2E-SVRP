package experiment;
import config.ExperimentParameters;

import java.util.Random;

/**
 * Mutable state for one independent search run.
 * Search/operator randomness and Monte Carlo randomness are kept separate.
 *
 * The Monte Carlo stream seeds are taken directly from config.properties:
 * simulationSearchSeed and finalSimulationSeed.
 * No run-seed addition, hexadecimal salt, or top-level seed mixing is applied here.
 */
public final class RunContext {

    private final Random searchRandomGenerator;
    private final Random simulationRandomGenerator;
    private final long simulationSearchSeed;
    private final long finalSimulationValidationSeed;
    private final ExperimentReporter reporter;

    public RunContext(int searchSeed, ExperimentReporter reporter) {
        this.searchRandomGenerator = new Random(searchSeed);

        // Direct config-controlled Monte Carlo streams.
        this.simulationSearchSeed = ExperimentParameters.simulationSearchSeed;
        this.simulationRandomGenerator = new Random(simulationSearchSeed);
        this.finalSimulationValidationSeed = ExperimentParameters.finalSimulationSeed;
        this.reporter = reporter;
    }

    /** The one shared search RNG for this independent heuristic run. */
    public Random getSearchRandomGenerator() {
        return searchRandomGenerator;
    }

    /**
     * Returns the search-time Monte Carlo seed. With CRN enabled every
     * alternative receives exactly simulationSearchSeed from the config.
     * With CRN disabled, simulationSearchSeed initializes a reproducible stream
     * of per-evaluation seeds.
     */
    public synchronized long nextSimulationEvaluationSeed() {
        if (ExperimentParameters.simulationUseCommonRandomNumbers) {
            return simulationSearchSeed;
        }
        return simulationRandomGenerator.nextLong();
    }

    /** Exact config value used by final Monte Carlo validation. */
    public long getFinalSimulationValidationSeed() {
        return finalSimulationValidationSeed;
    }

    public ExperimentReporter getReporter() {
        return reporter;
    }
}
