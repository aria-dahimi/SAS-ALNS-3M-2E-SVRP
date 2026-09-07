package problem;

import config.ProblemParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;

import static org.junit.jupiter.api.Assertions.*;

class VirtualMeetingPointStateTest {

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
        ProblemParameters.timeHorizonMinutes = 1000;
    }

    @Test
    void totalWeightCacheTracksCustomerMembershipChanges() {
        Depot depot = new Depot("D1", 0, 0, 0, 0, 1000, 0);
        MeetingPoint meeting = new MeetingPoint("S1", 1, 1, 0, 1000, 10);
        VirtualMeetingPoint virtualMeeting = new VirtualMeetingPoint("S1v1", meeting);
        Customer first = new Customer("C1", depot, 2, 2, 0, 100, 10, 10);
        Customer second = new Customer("C2", depot, 3, 3, 0, 100, 20, 10);

        virtualMeeting.customers.addCustomer(first);
        assertEquals(10.0, virtualMeeting.getTotalWeight(), 1e-12);

        virtualMeeting.customers.addCustomer(second);
        assertEquals(30.0, virtualMeeting.getTotalWeight(), 1e-12);

        virtualMeeting.customers.removeCustomer(first);
        assertEquals(20.0, virtualMeeting.getTotalWeight(), 1e-12);
    }

    @Test
    void clearVirtualMeetingRemovesTransientAssignmentState() {
        Depot depot = new Depot("D1", 0, 0, 0, 0, 1000, 0);
        Parking parking = new Parking("P1", 0, 0, 0, 0, 1000, 0);
        MeetingPoint meeting = new MeetingPoint("S1", 1, 1, 5, 900, 10);
        VirtualMeetingPoint virtualMeeting = new VirtualMeetingPoint("S1v1", meeting);
        Customer customer = new Customer("C1", depot, 2, 2, 0, 100, 10, 10);
        FirstEchelonVehicle fev = new FirstEchelonVehicle(1, 1, 100, 1.0, 0, 0.0, 0.0, depot);
        SecondEchelonVehicle sev = new SecondEchelonVehicle(1, 2, 100, 1.0, 0, 0.0, 0.0, parking);

        virtualMeeting.depot = depot;
        virtualMeeting.parking = parking;
        virtualMeeting.customers.addCustomer(customer);
        virtualMeeting.firstEchelonVehicle = fev;
        virtualMeeting.secondEchelonVehicle = sev;
        virtualMeeting.firstEchelonVisitTime = 25;
        virtualMeeting.secondEchelonVisitTime = 25;

        virtualMeeting.clearVirtualMeeting();

        assertNull(virtualMeeting.depot);
        assertNull(virtualMeeting.parking);
        assertEquals(0, virtualMeeting.customers.getCustomerCount());
        assertNull(virtualMeeting.firstEchelonVehicle);
        assertNull(virtualMeeting.secondEchelonVehicle);
        assertEquals(0.0, virtualMeeting.firstEchelonVisitTime, 1e-12);
        assertEquals(0.0, virtualMeeting.secondEchelonVisitTime, 1e-12);
        assertEquals(meeting.readyTime, virtualMeeting.readyTime);
        assertEquals(meeting.dueTime, virtualMeeting.dueTime);
    }
}
