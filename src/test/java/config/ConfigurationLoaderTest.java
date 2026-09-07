package config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import problem.ProblemVariant;
import testsupport.TestConfigSupport;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationLoaderTest {

    @BeforeEach
    void restoreValidConfiguration() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void loadsCompleteValidConfiguration() {
        ProjectSettings settings = TestConfigSupport.applyBaseConfiguration();

        assertEquals(ExecutionEnvironment.LOCAL, settings.getExecutionEnvironment());
        assertEquals("input/3M-2E-VRP".replace('/', java.io.File.separatorChar),
                settings.getInstanceDirectory().toString());
        assertEquals("result", settings.getResultDirectory().toString());
        assertEquals(ProblemVariant.THREE_M_2E_VRP, settings.getSelectedProblemVariant());
        assertNull(ProblemParameters.problemVariant);
        assertEquals(EvaluationMode.DETERMINISTIC, ExperimentParameters.evaluationMode);
        assertNull(ExperimentParameters.stochasticMode);
        assertEquals(18, ExperimentParameters.numberOfOperators);
    }

    @Test
    void rejectsUnknownProperty() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("unknown.parameter", "123");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("Unknown configuration property"));
    }


    @Test
    void rejectsFormerThreeMDistanceDivisorConfigKey() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("threeMDistanceDivisor", "6.0");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("Unknown configuration property"));
    }

    @Test
    void rejectsStochasticModeForDeterministicEvaluation() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("stochasticMode", "CCM");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("stochasticMode must be omitted"));
    }

    @Test
    void requiresStochasticModeForStochasticEvaluation() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("evaluationMode", "STOCHASTIC");
        properties.remove("stochasticMode");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("Missing required configuration property: stochasticMode"));
    }

    @Test
    void rejectsStochasticDellaertConfiguration() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("problemVariant", "DELLAERT-2E-VRP");
        properties.setProperty("evaluationMode", "STOCHASTIC");
        properties.setProperty("stochasticMode", "CCM");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("requires evaluationMode=DETERMINISTIC"));
    }

    @Test
    void rejectsExactSchedulingForDellaert() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("problemVariant", "DELLAERT-2E-VRP");
        properties.setProperty("schedulingMode", "EXACT");
        properties.setProperty("meetingPointAccessibility", "PARKING_ONLY");
        properties.setProperty("deterministicTimeMode", "MEAN");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("requires schedulingMode=APPROXIMATE"));
    }

    @Test
    void acceptsDetailedSchedulingDiagnosticsFlag() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("schedulingDiagnosticsDetailed", "true");

        TestConfigSupport.apply(properties);

        assertTrue(ExperimentParameters.schedulingDiagnosticsDetailed);
    }

    @Test
    void rejectsExactSchedulingForStochasticEvaluation() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("evaluationMode", "STOCHASTIC");
        properties.setProperty("stochasticMode", "CCM");
        properties.setProperty("schedulingMode", "EXACT");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("requires schedulingMode=APPROXIMATE"));
    }

    @Test
    void rejectsMoreThanEighteenOperators() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("numberOfOperators", "19");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("between 1 and 18"));
    }

    @Test
    void rejectsZeroConsoleProgressInterval() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("output.consoleProgressInterval", "0");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("output.consoleProgressInterval must be positive"));
    }

    @Test
    void rejectsInvalidTailStrategy() {
        Properties properties = TestConfigSupport.baseProperties();
        properties.setProperty("stochasticTailStrategy", "UNKNOWN");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TestConfigSupport.apply(properties));

        assertTrue(error.getMessage().contains("stochasticTailStrategy must be one of"));
    }
}
