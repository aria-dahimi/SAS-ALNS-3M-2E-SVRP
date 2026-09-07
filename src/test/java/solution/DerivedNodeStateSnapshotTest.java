package solution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import problem.Customer;
import problem.MeetingPoint;
import problem.VirtualMeetingPoint;
import testsupport.TestConfigSupport;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DerivedNodeStateSnapshotTest {

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void routeTrialSnapshotRestoresEmbeddedCustomersOfInsertedMeeting() {
        Customer customer = new Customer();
        customer.id = "C1";
        customer.readyTime = 10;
        customer.dueTime = 50;
        customer.secondEchelonArrivalTime = 21.0;
        customer.secondEchelonVisitTime = 22.0;
        customer.secondEchelonFailureProbability = 0.12;
        customer.secondEchelonCdfGeneration = 7L;

        MeetingPoint physicalMeeting = new MeetingPoint("M1", 0, 0, 0, 100, 10);
        VirtualMeetingPoint meeting = new VirtualMeetingPoint("M1v1", physicalMeeting);
        meeting.customers.addCustomer(customer);
        meeting.dueTime = 40;
        meeting.firstEchelonArrivalTime = 11.0;
        meeting.firstEchelonVisitTime = 12.0;
        meeting.firstEchelonCdfGeneration = 6L;

        DerivedNodeStateSnapshot snapshot = DerivedNodeStateSnapshot.captureRouteTrial(
                Collections.emptyList(), meeting);

        meeting.dueTime = 99;
        meeting.firstEchelonArrivalTime = 77.0;
        meeting.firstEchelonVisitTime = 78.0;
        meeting.firstEchelonCdfGeneration = 100L;
        customer.dueTime = 88;
        customer.secondEchelonArrivalTime = 66.0;
        customer.secondEchelonVisitTime = 67.0;
        customer.secondEchelonFailureProbability = 0.91;
        customer.secondEchelonCdfGeneration = 101L;

        snapshot.restore();

        assertEquals(40, meeting.dueTime);
        assertEquals(11.0, meeting.firstEchelonArrivalTime);
        assertEquals(12.0, meeting.firstEchelonVisitTime);
        assertEquals(6L, meeting.firstEchelonCdfGeneration);
        assertEquals(50, customer.dueTime);
        assertEquals(21.0, customer.secondEchelonArrivalTime);
        assertEquals(22.0, customer.secondEchelonVisitTime);
        assertEquals(0.12, customer.secondEchelonFailureProbability);
        assertEquals(7L, customer.secondEchelonCdfGeneration);
    }
}
