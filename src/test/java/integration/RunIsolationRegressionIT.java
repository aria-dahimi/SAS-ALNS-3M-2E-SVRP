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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that an earlier run does not change the result of a later seed. */
class RunIsolationRegressionIT {

    @Test
    void seed1231IsIdenticalWhetherRunAloneOrAfterSeed1230() throws Exception {
        configureFastIntegrationRun();
        Fixture fixture = fixture();

        run(fixture, 1230); // Run an earlier seed first to check run isolation.
        SearchSnapshot afterPreviousRun = run(fixture, 1231);
        SearchSnapshot alone = run(fixture, 1231);

        assertEquals(alone.objective, afterPreviousRun.objective, 1e-9);
        assertEquals(alone.bestIteration, afterPreviousRun.bestIteration);
        assertEquals(alone.events, afterPreviousRun.events);
        assertEquals(alone.firstEchelonVehicles, afterPreviousRun.firstEchelonVehicles);
        assertEquals(alone.secondEchelonVehicles, afterPreviousRun.secondEchelonVehicles);
    }

    private void configureFastIntegrationRun() {
        TestConfigSupport.applyConfiguration(Map.of(
                "initialSolutionAttempts", "20",
                "maxSaIterations", "1",
                "maxLnsIterations", "100",
                "operatorRepetitions", "5",
                "validation.saCheckpointInterval", "0",
                "validation.finalSolution", "false",
                "validation.finalExactSchedule", "false"
        ));
    }

    private Fixture fixture() throws Exception {
        Path instance = resourcePath("/instances/3M-Cb-2-3-50-2.txt");
        int initialCopies = (int) (50 * ExperimentParameters.initialVirtualMeetingCopiesPerCustomer);
        ProblemInstance problem = new BenchmarkInstanceReader(
                ProblemVariant.THREE_M_2E_VRP, 2, 3)
                .read(instance, new StochasticData(), initialCopies);
        Random setupRandom = new Random(ExperimentParameters.distributionSeed);
        SearchProblemTemplates templates = SearchProblemTemplates.create(problem, setupRandom);
        return new Fixture(problem, templates);
    }

    private SearchSnapshot run(Fixture fixture, int seed) {
        SearchEngine engine = new SearchEngine(
                fixture.problem,
                fixture.templates,
                new RunContext(seed, null));
        engine.execute();
        return new SearchSnapshot(
                engine.finalSolution.objective,
                engine.bestTotalIteration,
                engine.finalSolution.getMeetingPointCount(),
                engine.finalSolution.getUsedFirstEchelonVehicleCount(),
                engine.finalSolution.getUsedSecondEchelonVehicleCount());
    }

    private Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(RunIsolationRegressionIT.class.getResource(resource).toURI());
    }

    private record Fixture(ProblemInstance problem, SearchProblemTemplates templates) {
    }

    private record SearchSnapshot(
            double objective,
            int bestIteration,
            int events,
            int firstEchelonVehicles,
            int secondEchelonVehicles) {
    }
}
