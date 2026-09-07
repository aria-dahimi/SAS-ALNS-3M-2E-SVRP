package search;

import config.ExperimentParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedAnnealingWeightResetTest {

    @AfterEach
    void restoreDefaults() {
        ExperimentParameters.adaptiveOperatorWeightsEnabled = false;
        ExperimentParameters.operatorWeightResetInterval = 0;
    }

    @Test
    void disabledAdaptiveWeightsNeverUseZeroResetInterval() {
        ExperimentParameters.adaptiveOperatorWeightsEnabled = false;
        ExperimentParameters.operatorWeightResetInterval = 0;

        assertFalse(SimulatedAnnealing.shouldResetOperatorWeights(1));
        assertFalse(SimulatedAnnealing.shouldResetOperatorWeights(100));
    }

    @Test
    void enabledAdaptiveWeightsResetOnlyAtConfiguredInterval() {
        ExperimentParameters.adaptiveOperatorWeightsEnabled = true;
        ExperimentParameters.operatorWeightResetInterval = 10;

        assertFalse(SimulatedAnnealing.shouldResetOperatorWeights(9));
        assertTrue(SimulatedAnnealing.shouldResetOperatorWeights(10));
        assertTrue(SimulatedAnnealing.shouldResetOperatorWeights(20));
    }
}
