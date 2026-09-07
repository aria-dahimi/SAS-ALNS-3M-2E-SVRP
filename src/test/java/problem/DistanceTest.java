package problem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;

import static org.junit.jupiter.api.Assertions.*;

class DistanceTest {

    private Depot origin;
    private Customer destination;

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
        origin = new Depot("D1", 0, 0, 0, 0, 1000, 0);
        destination = new Customer("C1", origin, 30, 40, 0, 100, 10, 10);
    }

    @Test
    void computesRawEuclideanDistance() {
        assertEquals(50.0, Distance.getEuclideanDistance(origin, destination), 1e-12);
    }

    @Test
    void threeMUsesDistanceDefinitionOwnedByInstance() {
        ProblemInstance problem = new ProblemInstance(
                DistanceMetric.FLOOR_EUCLIDEAN_DIVISOR, 6.0);
        origin.problem = problem;
        destination.problem = problem;

        assertEquals(8.0, Distance.getDistance(origin, destination), 1e-12);
        assertEquals(8.0, Distance.getTravelDistance(origin, destination), 1e-12);
    }

    @Test
    void rawEuclideanInstanceUsesRawEuclideanDistance() {
        ProblemInstance problem = new ProblemInstance(DistanceMetric.RAW_EUCLIDEAN, 1.0);
        origin.problem = problem;
        destination.problem = problem;

        assertEquals(50.0, Distance.getDistance(origin, destination), 1e-12);
        assertEquals(50.0, Distance.getTravelDistance(origin, destination), 1e-12);
    }

    @Test
    void rejectsTransformedDistanceWithoutInstanceOwnership() {
        assertThrows(IllegalStateException.class,
                () -> Distance.getDistance(origin, destination));
    }

    @Test
    void rejectsNodeWithoutCoordinates() {
        VirtualMeetingPoint missingCoordinates = new VirtualMeetingPoint();
        assertThrows(IllegalArgumentException.class,
                () -> Distance.getEuclideanDistance(origin, missingCoordinates));
    }
}
