package solution;

import config.ProblemParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import problem.Customer;
import problem.Depot;
import problem.FirstEchelonVehicle;
import problem.ProblemVariant;
import problem.VirtualMeetingPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatedMeetingRuleTest {

    private FirstEchelonVehicle fev1;
    private FirstEchelonVehicle fev2;

    @BeforeEach
    void setUp() {
        ProblemParameters.problemVariant = ProblemVariant.THREE_M_2E_VRP;
        Depot depot = new Depot("D1", 1, 0, 0, 0, 600, 0);
        fev1 = new FirstEchelonVehicle(1, 1, 1000, 1.0, 0, 0.0, 0.0, depot);
        fev2 = new FirstEchelonVehicle(2, 1, 1000, 1.0, 0, 0.0, 0.0, depot);
    }

    @Test
    void rejectsSameFevMeetingAgainBeforeAnyDelivery() {
        SecondEchelonRoute route = new SecondEchelonRoute();
        route.route.add(meeting("M1", fev1));
        route.route.add(meeting("M2", fev2));
        route.route.add(meeting("M3", fev1));

        assertEquals(1, route.countRepeatedVehicleMeetingViolations());
    }

    @Test
    void allowsSameFevMeetingAgainAfterCustomerDelivery() {
        SecondEchelonRoute route = new SecondEchelonRoute();
        route.route.add(meeting("M1", fev1));
        route.route.add(new Customer());
        route.route.add(meeting("M2", fev1));

        assertEquals(0, route.countRepeatedVehicleMeetingViolations());
    }

    private static VirtualMeetingPoint meeting(String id, FirstEchelonVehicle fev) {
        VirtualMeetingPoint meeting = new VirtualMeetingPoint();
        meeting.id = id;
        meeting.firstEchelonVehicle = fev;
        return meeting;
    }
}
