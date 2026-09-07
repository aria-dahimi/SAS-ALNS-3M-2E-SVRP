package experiment;

import config.ExperimentParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RunContextTest {

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void searchRandomGeneratorStartsFromConfiguredRunSeed() {
        RunContext context = new RunContext(1230, null);
        Random expected = new Random(1230);

        assertEquals(expected.nextInt(), context.getSearchRandomGenerator().nextInt());
        assertEquals(expected.nextDouble(), context.getSearchRandomGenerator().nextDouble(), 0.0);
    }

    @Test
    void differentRunsOwnDifferentSearchRandomGenerators() {
        RunContext first = new RunContext(1230, null);
        RunContext second = new RunContext(1231, null);

        assertNotSame(first.getSearchRandomGenerator(), second.getSearchRandomGenerator());
        assertNotEquals(
                first.getSearchRandomGenerator().nextLong(),
                second.getSearchRandomGenerator().nextLong());
    }

    @Test
    void simulationSeedStreamIsIndependentFromSearchRandomDraws() {
        RunContext untouchedSearch = new RunContext(1230, null);
        RunContext consumedSearch = new RunContext(1230, null);

        for (int i = 0; i < 100; i++) {
            consumedSearch.getSearchRandomGenerator().nextDouble();
        }

        assertEquals(
                untouchedSearch.nextSimulationEvaluationSeed(),
                consumedSearch.nextSimulationEvaluationSeed());
    }

    @Test
    void commonRandomNumbersReuseOneSearchScenarioSeed() {
        ExperimentParameters.simulationUseCommonRandomNumbers = true;
        RunContext context = new RunContext(1230, null);

        long first = context.nextSimulationEvaluationSeed();
        long second = context.nextSimulationEvaluationSeed();

        assertEquals(first, second);
    }

    @Test
    void finalMonteCarloSeedIsIndependentFromSearchEvaluationCalls() {
        ExperimentParameters.simulationUseCommonRandomNumbers = false;
        RunContext context = new RunContext(1230, null);
        long validationSeed = context.getFinalSimulationValidationSeed();

        for (int i = 0; i < 20; i++) {
            context.nextSimulationEvaluationSeed();
        }

        assertEquals(validationSeed, context.getFinalSimulationValidationSeed());
    }

    @Test
    void simulationSeedSequenceIsReproducible() {
        ExperimentParameters.simulationSearchSeed = 987654321L;
        RunContext first = new RunContext(1230, null);
        RunContext second = new RunContext(1230, null);

        for (int i = 0; i < 20; i++) {
            assertEquals(first.nextSimulationEvaluationSeed(), second.nextSimulationEvaluationSeed());
        }
    }

    @Test
    void changingHeuristicSearchSeedDoesNotChangeConfiguredCrnScenarioSeed() {
        ExperimentParameters.simulationUseCommonRandomNumbers = true;
        ExperimentParameters.simulationSearchSeed = 424242L;

        RunContext first = new RunContext(1230, null);
        RunContext second = new RunContext(9999, null);

        assertEquals(424242L, first.nextSimulationEvaluationSeed());
        assertEquals(424242L, second.nextSimulationEvaluationSeed());
    }

    @Test
    void configuredSimulationSeedsAreUsedDirectly() {
        ExperimentParameters.simulationUseCommonRandomNumbers = true;
        ExperimentParameters.simulationSearchSeed = 111L;
        ExperimentParameters.finalSimulationSeed = 333L;

        RunContext context = new RunContext(9999, null);

        assertEquals(111L, context.nextSimulationEvaluationSeed());
        assertEquals(333L, context.getFinalSimulationValidationSeed());
    }
}
