package stochastic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FFunctionInterpolationTest {

    private static final double[][] CDF = {
            {10.0, 0.10},
            {20.0, 0.50},
            {30.0, 0.90}
    };

    @Test
    void returnsZeroBelowLowerSupport() {
        assertEquals(0.0, FFunction.findFx(9.0, CDF), 1e-12);
    }

    @Test
    void preservesStoredMassAtLowerSupport() {
        assertEquals(0.10, FFunction.findFx(10.0, CDF), 1e-12);
    }

    @Test
    void linearlyInterpolatesBetweenSupportPoints() {
        assertEquals(0.30, FFunction.findFx(15.0, CDF), 1e-12);
        assertEquals(0.70, FFunction.findFx(25.0, CDF), 1e-12);
    }

    @Test
    void returnsOneAtAndAboveUpperSupport() {
        assertEquals(1.0, FFunction.findFx(30.0, CDF), 1e-12);
        assertEquals(1.0, FFunction.findFx(100.0, CDF), 1e-12);
    }

    @Test
    void rejectsMalformedCdf() {
        assertThrows(IllegalArgumentException.class, () -> FFunction.findFx(1.0, new double[0][0]));
        assertThrows(IllegalArgumentException.class, () -> FFunction.findFx(1.0, new double[][]{{1.0}}));
    }
}
