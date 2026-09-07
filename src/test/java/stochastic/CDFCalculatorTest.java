package stochastic;

import config.ExperimentParameters;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;

import static org.junit.jupiter.api.Assertions.*;

class CDFCalculatorTest {

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void truncatedTravelCdfStartsAtZeroAndEndsAtOne() {
        ExperimentParameters.stochasticTailStrategy = "TRUNCATION";
        LogNormalDistribution distribution = new LogNormalDistribution(2.0, 0.3);
        double lower = distribution.inverseCumulativeProbability(ExperimentParameters.stochasticLowerQuantile);
        double upper = distribution.inverseCumulativeProbability(ExperimentParameters.stochasticUpperQuantile);

        double[][] table = new CDFCalculator().precomputeTravelCdf(distribution, lower, upper, 20);

        assertEquals(0.0, table[0][1], 1e-12);
        assertEquals(1.0, table[table.length - 1][1], 1e-12);
        assertMonotone(table);
    }

    @Test
    void clippedTravelCdfRetainsLowerTailMass() {
        ExperimentParameters.stochasticTailStrategy = "CLIPPING";
        LogNormalDistribution distribution = new LogNormalDistribution(2.0, 0.3);
        double lower = distribution.inverseCumulativeProbability(ExperimentParameters.stochasticLowerQuantile);
        double upper = distribution.inverseCumulativeProbability(ExperimentParameters.stochasticUpperQuantile);

        double[][] table = new CDFCalculator().precomputeTravelCdf(distribution, lower, upper, 20);

        assertEquals(ExperimentParameters.stochasticLowerQuantile, table[0][1], 1e-12);
        assertEquals(1.0, table[table.length - 1][1], 1e-12);
        assertMonotone(table);
    }

    @Test
    void serviceTravelConvolutionIsMonotoneAndNormalizedUnderTruncation() {
        ExperimentParameters.stochasticTailStrategy = "TRUNCATION";
        LogNormalDistribution service = new LogNormalDistribution(2.0, 0.25);
        LogNormalDistribution travel = new LogNormalDistribution(1.5, 0.35);
        double serviceLower = service.inverseCumulativeProbability(ExperimentParameters.stochasticLowerQuantile);
        double serviceUpper = service.inverseCumulativeProbability(ExperimentParameters.stochasticUpperQuantile);
        double travelLower = travel.inverseCumulativeProbability(ExperimentParameters.stochasticLowerQuantile);
        double travelUpper = travel.inverseCumulativeProbability(ExperimentParameters.stochasticUpperQuantile);

        double[][] table = new CDFCalculator().precomputeServiceTravelConvolution(
                service, travel, serviceLower, serviceUpper, travelLower, travelUpper, 25);

        assertEquals(0.0, table[0][1], 1e-12);
        assertEquals(1.0, table[table.length - 1][1], 1e-12);
        assertMonotone(table);
    }

    @Test
    void trimCdfSupportRemovesOnlyTrailingZeroRows() {
        double[][] cdf = {
                {1.0, 0.0},
                {2.0, 0.2},
                {3.0, 0.8},
                {4.0, 0.0},
                {5.0, 0.0}
        };

        double[][] trimmed = CDFCalculator.trimCdfSupport(cdf);

        assertEquals(3, trimmed.length);
        assertEquals(3.0, trimmed[2][0], 1e-12);
        assertEquals(0.8, trimmed[2][1], 1e-12);
    }

    @Test
    void trimCdfSupportReturnsEmptyForAllZeroCdf() {
        double[][] cdf = {{1.0, 0.0}, {2.0, 0.0}};
        assertEquals(0, CDFCalculator.trimCdfSupport(cdf).length);
    }

    @Test
    void requestedGridPointCountIsStoredExactly() {
        ExperimentParameters.stochasticTailStrategy = "TRUNCATION";
        LogNormalDistribution distribution = new LogNormalDistribution(2.0, 0.3);
        double lower = distribution.inverseCumulativeProbability(ExperimentParameters.stochasticLowerQuantile);
        double upper = distribution.inverseCumulativeProbability(ExperimentParameters.stochasticUpperQuantile);

        double[][] table = new CDFCalculator().precomputeTravelCdf(distribution, lower, upper, 10);

        assertEquals(10, table.length);
        assertEquals(lower, table[0][0], 1e-12);
        assertEquals(upper, table[table.length - 1][0], 1e-12);
    }

    @Test
    void numericalCdfNormalizationRestoresCdfInvariants() {
        double[][] cdf = {
                {1.0, -1e-9},
                {2.0, 0.4},
                {3.0, 0.39},
                {4.0, 0.97}
        };

        CDFCalculator.normalizeCdfInPlace(cdf);

        assertEquals(0.0, cdf[0][1], 1e-12);
        assertEquals(0.4, cdf[1][1], 1e-12);
        assertEquals(0.4, cdf[2][1], 1e-12);
        assertEquals(1.0, cdf[3][1], 1e-12);
    }

    private void assertMonotone(double[][] table) {
        double previous = -1.0;
        for (double[] row : table) {
            assertTrue(row[1] >= previous - 1e-12, "CDF decreased");
            assertTrue(row[1] >= -1e-12 && row[1] <= 1.0 + 1e-12, "CDF outside [0,1]");
            previous = row[1];
        }
    }
}
