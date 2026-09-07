package config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentParametersTest {

    @BeforeEach
    void loadConfiguration() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void checkpointValidationCanBeDisabled() {
        ExperimentParameters.validationSaCheckpointInterval = 0;
        assertFalse(ExperimentParameters.shouldValidateAfterSaCycle(1));
        assertFalse(ExperimentParameters.shouldValidateAfterSaCycle(5));
    }

    @Test
    void checkpointValidationUsesConfiguredCycleInterval() {
        ExperimentParameters.validationSaCheckpointInterval = 2;
        assertFalse(ExperimentParameters.shouldValidateAfterSaCycle(1));
        assertTrue(ExperimentParameters.shouldValidateAfterSaCycle(2));
        assertFalse(ExperimentParameters.shouldValidateAfterSaCycle(3));
        assertTrue(ExperimentParameters.shouldValidateAfterSaCycle(4));
    }

    @Test
    void evaluationModeHelpersAreConsistent() {
        ExperimentParameters.evaluationMode = EvaluationMode.DETERMINISTIC;
        ExperimentParameters.stochasticMode = null;
        assertTrue(ExperimentParameters.isDeterministicEvaluation());
        assertFalse(ExperimentParameters.isStochasticEvaluation());
        assertFalse(ExperimentParameters.usesCcm());
        assertFalse(ExperimentParameters.usesPbm());

        ExperimentParameters.evaluationMode = EvaluationMode.STOCHASTIC;
        ExperimentParameters.stochasticMode = StochasticMode.CCM;
        assertTrue(ExperimentParameters.isStochasticEvaluation());
        assertTrue(ExperimentParameters.usesCcm());
        assertFalse(ExperimentParameters.usesPbm());

        ExperimentParameters.stochasticMode = StochasticMode.PBM;
        assertFalse(ExperimentParameters.usesCcm());
        assertTrue(ExperimentParameters.usesPbm());
    }
}
