package search;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OperatorWeightTableTest {

    @Test
    void rejectsNullRandomGenerator() {
        assertThrows(IllegalArgumentException.class, () -> new OperatorWeightTable(null));
    }

    @Test
    void rejectsSelectionFromEmptyTable() {
        OperatorWeightTable table = new OperatorWeightTable(new Random(1230));
        assertThrows(IllegalStateException.class, table::selectOperatorIndex);
    }

    @Test
    void onePositiveWeightIsAlwaysSelectedWhenOthersAreZero() {
        OperatorWeightTable table = new OperatorWeightTable(new Random(1230));
        table.setWeight(0, 0.0);
        table.setWeight(1, 0.0);
        table.setWeight(2, 5.0);

        for (int i = 0; i < 100; i++) {
            assertEquals(2, table.selectOperatorIndex());
        }
    }

    @Test
    void sameSeedAndWeightsProduceSameSelectionSequence() {
        OperatorWeightTable first = populatedTable(1230);
        OperatorWeightTable second = populatedTable(1230);

        for (int i = 0; i < 100; i++) {
            assertEquals(first.selectOperatorIndex(), second.selectOperatorIndex());
        }
    }

    @Test
    void missingWeightIsReportedClearly() {
        OperatorWeightTable table = new OperatorWeightTable(new Random(1));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> table.getWeight(4));
        assertTrue(error.getMessage().contains("No weight configured"));
    }

    private OperatorWeightTable populatedTable(long seed) {
        OperatorWeightTable table = new OperatorWeightTable(new Random(seed));
        table.setWeight(0, 0.5);
        table.setWeight(1, 1.0);
        table.setWeight(2, 2.0);
        table.setWeight(3, 4.0);
        return table;
    }
}
