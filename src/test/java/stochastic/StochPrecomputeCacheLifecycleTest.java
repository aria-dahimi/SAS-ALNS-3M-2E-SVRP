package stochastic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StochPrecomputeCacheLifecycleTest {

    @AfterEach
    void cleanup() {
        StochPrecomputeCache.endInstance(null);
    }

    @Test
    void cacheCanBeReopenedForNextInstanceAfterClose() {
        assertDoesNotThrow(() -> StochPrecomputeCache.beginInstance("instance-A"));
        assertDoesNotThrow(() -> StochPrecomputeCache.clear());
        assertDoesNotThrow(() -> StochPrecomputeCache.endInstance("instance-A"));

        assertDoesNotThrow(() -> StochPrecomputeCache.beginInstance("instance-B"));
        assertDoesNotThrow(() -> StochPrecomputeCache.endInstance("instance-B"));
    }

    @Test
    void overlappingInstanceScopesAreRejected() {
        StochPrecomputeCache.beginInstance("instance-A");
        assertThrows(IllegalStateException.class,
                () -> StochPrecomputeCache.beginInstance("instance-B"));
    }

    @Test
    void closingDifferentInstanceIsRejected() {
        StochPrecomputeCache.beginInstance("instance-A");
        assertThrows(IllegalStateException.class,
                () -> StochPrecomputeCache.endInstance("instance-B"));
    }
}
