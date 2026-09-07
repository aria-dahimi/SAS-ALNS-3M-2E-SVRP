package stochastic;

import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastLogNormalSamplerTest {

    @Test
    void rejectsNullDistribution() {
        assertThrows(IllegalArgumentException.class, () -> new FastLogNormalSampler(null));
    }

    @Test
    void fastInverseMatchesCommonsMathAcrossRepresentativeQuantiles() {
        LogNormalDistribution distribution = new LogNormalDistribution(2.1, 0.45);
        FastLogNormalSampler sampler = new FastLogNormalSampler(distribution);

        double[] probabilities = {0.001, 0.01, 0.05, 0.25, 0.5, 0.75, 0.95, 0.99, 0.999};
        for (double probability : probabilities) {
            double expected = distribution.inverseCumulativeProbability(probability);
            double actual = sampler.inverseCdf(probability);
            double relativeTolerance = Math.max(1e-10, Math.abs(expected) * 5e-7);
            assertEquals(expected, actual, relativeTolerance, "p=" + probability);
        }
    }
}
