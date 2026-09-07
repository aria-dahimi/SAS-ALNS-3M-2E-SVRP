package integration;

import config.ExperimentParameters;
import experiment.RunContext;
import experiment.SearchProblemTemplates;
import experiment.StochasticData;
import org.junit.jupiter.api.Test;
import problem.BenchmarkInstanceReader;
import problem.ProblemInstance;
import problem.ProblemVariant;
import search.SearchEngine;
import testsupport.TestConfigSupport;
import validation.SolutionValidationResult;
import validation.SolutionValidator;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the seed-1233 stale virtual-meeting failure.
 * Run with {@code mvn verify -Pintegration-tests}.
 */
class HistoricalSeed1233RegressionIT {

    @Test
    void seed1233CompletesWithoutStaleVirtualMeetingCrash() throws Exception {
        configureHistoricalRegression();
        ProblemInstance problem = loadRegressionProblem();
        Random setupRandom = new Random(ExperimentParameters.distributionSeed);
        SearchProblemTemplates templates = SearchProblemTemplates.create(problem, setupRandom);
        SearchEngine engine = new SearchEngine(problem, templates, new RunContext(1233, null));

        assertDoesNotThrow(engine::execute);
        assertNotNull(engine.finalSolution);
        assertTrue(Double.isFinite(engine.finalSolution.objective));
        assertTrue(engine.finalSolution.objective < Integer.MAX_VALUE);

        SolutionValidationResult validation = SolutionValidator.validate(engine.finalSolution);
        assertTrue(validation.isValid(), validation::formatErrors);
    }

    private void configureHistoricalRegression() {
        TestConfigSupport.applyConfiguration(Map.of(
                "initialSolutionAttempts", "100",
                "maxSaIterations", "1",
                "maxLnsIterations", "1000",
                "operatorRepetitions", "10",
                "operatorWeightResetInterval", "1000",
                "validation.saCheckpointInterval", "0",
                "validation.finalSolution", "false",
                "validation.finalExactSchedule", "false"
        ));
    }

    private ProblemInstance loadRegressionProblem() throws Exception {
        Path instance = resourcePath("/instances/3M-Cb-2-3-50-2.txt");
        int initialCopies = (int) (50 * ExperimentParameters.initialVirtualMeetingCopiesPerCustomer);
        return new BenchmarkInstanceReader(ProblemVariant.THREE_M_2E_VRP, 2, 3)
                .read(instance, new StochasticData(), initialCopies);
    }

    private Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(HistoricalSeed1233RegressionIT.class.getResource(resource).toURI());
    }
}
