package experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StochasticDataTest {

    @Test
    void distributionViewsAreReadOnly() {
        StochasticData data = new StochasticData();

        assertTrue(data.serviceDistributionsView().isEmpty());
        assertTrue(data.travelDistributionsView().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> data.serviceDistributionsView().put("x", null));
        assertThrows(UnsupportedOperationException.class,
                () -> data.travelDistributionsView().put("x", null));
    }

    @Test
    void initializationRejectsMissingInputs() {
        StochasticData data = new StochasticData();
        assertThrows(IllegalArgumentException.class, () -> data.initialize(null));
    }
}
