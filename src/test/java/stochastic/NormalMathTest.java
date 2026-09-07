package stochastic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalMathTest {

    @Test
    void medianOfStandardNormalIsZero() {
        assertEquals(0.0, NormalMath.inverseCdf(0.5), 1e-12);
    }

    @Test
    void commonQuantileMatchesReferenceValue() {
        assertEquals(1.959963984540054, NormalMath.inverseCdf(0.975), 5e-8);
    }

    @Test
    void inverseCdfIsSymmetric() {
        double lower = NormalMath.inverseCdf(0.1);
        double upper = NormalMath.inverseCdf(0.9);
        assertEquals(-lower, upper, 1e-8);
    }

    @Test
    void endpointAndInvalidProbabilityBehaviorIsExplicit() {
        assertEquals(Double.NEGATIVE_INFINITY, NormalMath.inverseCdf(0.0));
        assertEquals(Double.POSITIVE_INFINITY, NormalMath.inverseCdf(1.0));
        assertThrows(IllegalArgumentException.class, () -> NormalMath.inverseCdf(-0.1));
        assertThrows(IllegalArgumentException.class, () -> NormalMath.inverseCdf(1.1));
        assertThrows(IllegalArgumentException.class, () -> NormalMath.inverseCdf(Double.NaN));
    }
}
