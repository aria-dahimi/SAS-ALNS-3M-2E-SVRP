package search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeighborhoodOperatorTest {

    @Test
    void publishedOperatorIndexesRemainStable() {
        NeighborhoodOperator[] operators = NeighborhoodOperator.values();
        assertEquals(18, operators.length);

        for (int expectedIndex = 0; expectedIndex < operators.length; expectedIndex++) {
            assertEquals(expectedIndex, operators[expectedIndex].index());
            assertSame(operators[expectedIndex], NeighborhoodOperator.fromIndex(expectedIndex));
        }
    }

    @Test
    void reportNamesAreExplicitAndNonBlank() {
        for (NeighborhoodOperator operator : NeighborhoodOperator.values()) {
            assertNotNull(operator.reportName());
            assertFalse(operator.reportName().isBlank());
        }
    }

    @Test
    void rejectsUnknownOperatorIndex() {
        assertThrows(IllegalArgumentException.class, () -> NeighborhoodOperator.fromIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> NeighborhoodOperator.fromIndex(18));
    }
}
